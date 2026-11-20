package org.openvoipalliance.androidphoneintegration.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.CallSessionState
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.call.CallArrayList
import org.openvoipalliance.androidphoneintegration.call.CallDirection
import org.openvoipalliance.androidphoneintegration.call.CallState
import org.openvoipalliance.androidphoneintegration.call.Calls
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallConnected
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallDurationUpdated
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallStateUpdated
import org.openvoipalliance.androidphoneintegration.events.EventsManager
import org.openvoipalliance.androidphoneintegration.installKoin
import org.openvoipalliance.androidphoneintegration.mockLinphoneFactory
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.notifications.CallNotification
import org.openvoipalliance.androidphoneintegration.notifications.IncomingCallNotification
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.androidphoneintegration.restoreLinphoneFactory
import org.openvoipalliance.androidphoneintegration.telecom.Connection
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import java.util.concurrent.TimeUnit
import org.linphone.core.tools.Log as LinphoneLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoIPServiceTest {
    private val pil = mockPil()
    private val calls = spyk(Calls(mockk(relaxed = true), CallArrayList(Calls.MAX_CALLS)))
    private val events = mockk<EventsManager>(relaxed = true)
    private val callNotification = mockk<CallNotification>(relaxed = true)
    private val incomingCallNotification = mockk<IncomingCallNotification>(relaxed = true)
    private val ongoing = Notification()
    private val incoming = Notification()
    private val connection = mockk<Connection>(relaxed = true)
    private val state = mockk<CallSessionState>()
    private lateinit var service: VoIPService

    @Before
    fun setUp() {
        mockLinphoneFactory()
        // CoreService's lifecycle logs through linphone's native logger.
        mockkStatic(LinphoneLog::class)
        every { LinphoneLog.i(*anyVararg()) } returns Unit
        every { LinphoneLog.w(*anyVararg()) } returns Unit
        installKoin {
            single { callNotification }
            single { incomingCallNotification }
            single { application.getSystemService(NotificationManager::class.java) }
        }
        every { pil.calls } returns calls
        every { pil.events } returns events
        every { calls.active } returns null
        every { pil.androidCallFramework.connection } returns connection
        every { callNotification.notificationId } returns 4567
        every { callNotification.build(any()) } returns ongoing
        every { incomingCallNotification.notificationId } returns 2435
        every { incomingCallNotification.build(any()) } returns incoming
        service = Robolectric.buildService(VoIPService::class.java).get()
    }

    @After
    fun tearDown() {
        clearPilInstance()
        restoreLinphoneFactory()
        unmockkStatic(LinphoneLog::class)
    }

    @Test
    fun `a ringing incoming call shows the incoming call notification`() {
        val call = pilCall(direction = CallDirection.INBOUND, state = CallState.RINGING)
        every { calls.active } returns call
        every { calls.inactive } returns null

        service.onEvent(CallStateUpdated(state))

        verify { incomingCallNotification.build(call) }
        assertForeground(2435, incoming)
    }

    @Test
    fun `other calls show the ongoing call notification`() {
        val outgoing = pilCall(direction = CallDirection.OUTBOUND, state = CallState.RINGING)
        every { calls.active } returns outgoing
        service.onEvent(CallStateUpdated(state))
        verify { callNotification.build(outgoing) }
        assertForeground(4567, ongoing)

        val connected = pilCall(direction = CallDirection.INBOUND, state = CallState.CONNECTED)
        every { calls.active } returns connected
        service.onEvent(CallStateUpdated(state))
        verify { callNotification.build(connected) }
    }

    @Test
    fun `an incoming call during a transfer shows the ongoing call notification`() {
        every { calls.active } returns pilCall(direction = CallDirection.INBOUND, state = CallState.RINGING)
        every { calls.inactive } returns pilCall()

        service.onEvent(CallStateUpdated(state))

        assertForeground(4567, ongoing)
    }

    @Test
    fun `a connected call reroutes audio`() {
        service.onEvent(CallConnected(state))

        verify { connection.updateCurrentRouteBasedOnAudioState() }
        assertForeground(4567, ongoing)
    }

    @Test
    fun `starting listens to events, keeps the screen off near the ear and updates the duration`() {
        calls.add(voipLibCall())

        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1))

        verify { events.listen(service) }
        assertForeground(4567, ongoing)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertTrue(wakeLock.isHeld)
        assertEquals("Vialer::ProximitySensor", shadowOf(wakeLock).tag)

        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        verify(atLeast = 2) { events.broadcast(CallDurationUpdated::class) }
        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `the service stops itself once there are no calls`() {
        service.onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `destroying releases the screen lock and the notification`() {
        service.onStartCommand(null, 0, 1)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()

        service.onDestroy()

        assertFalse(wakeLock.isHeld)
        verify {
            events.stopListening(service)
            callNotification.cancel()
        }
    }

    @Test
    fun `removing the task ends the calls`() {
        calls.add(voipLibCall())
        calls.add(voipLibCall())

        service.onTaskRemoved(null)

        verify(exactly = 2) { pil.actions.end() }
    }

    private fun assertForeground(id: Int, notification: Notification) {
        assertEquals(id, shadowOf(service).lastForegroundNotificationId)
        assertSame(notification, shadowOf(service).lastForegroundNotification)
    }
}
