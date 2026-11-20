package org.openvoipalliance.androidphoneintegration.android

import android.app.NotificationManager
import android.content.Context
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.CallSessionState
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.applicationSetup
import org.openvoipalliance.androidphoneintegration.audio.AudioRoute
import org.openvoipalliance.androidphoneintegration.audio.AudioState
import org.openvoipalliance.androidphoneintegration.call.CallFactory
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallConnected
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallEnded
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallStateUpdated
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.IncomingCallReceived
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.OutgoingCallStarted
import org.openvoipalliance.androidphoneintegration.installKoin
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.androidphoneintegration.telecom.AndroidCallFramework
import org.openvoipalliance.androidphoneintegration.telecom.Connection
import org.openvoipalliance.voiplib.model.Call
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformIntegratorTest {
    private val pil = mockPil()
    private val connection = mockk<Connection>(relaxed = true)
    private val androidCallFramework = mockk<AndroidCallFramework>(relaxed = true)
    private val factory = mockk<CallFactory>()
    private val integrator = PlatformIntegrator(pil, androidCallFramework, factory)
    private val call = pilCall(displayName = "Alice")
    private val state = CallSessionState(
        call,
        null,
        AudioState(AudioRoute.PHONE, emptyArray(), null, false, emptyArray()),
    )

    @Before
    fun setUp() {
        every { androidCallFramework.connection } returns connection
        every { pil.calls.active } returns call
        installKoin {
            single { pil }
            single<Context> { application }
            single { application.getSystemService(NotificationManager::class.java) }
        }
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `stale notifications are dismissed on every event`() {
        integrator.onEvent(Event.CallSetupFailedEvent.OutgoingCallSetupFailed(Event.CallSetupFailedEvent.Reason.IN_CALL))

        verify { pil.notifications.dismissStale() }
        verify(exactly = 0) { androidCallFramework.addNewIncomingCall(any()) }
    }

    @Test
    fun `events without an active call are ignored`() {
        integrator.onEvent(IncomingCallReceived(state.copy(activeCall = null)))

        verify(exactly = 0) { androidCallFramework.addNewIncomingCall(any()) }
    }

    @Test
    fun `an incoming call is added to telecom`() {
        integrator.onEvent(IncomingCallReceived(state))

        verify { androidCallFramework.addNewIncomingCall("+31501234567") }
    }

    @Test
    fun `an outgoing call dials the connection and shows the call`() {
        integrator.onEvent(OutgoingCallStarted(state))

        verify {
            connection.setCallerDisplayName("Alice", TelecomManager.PRESENTATION_ALLOWED)
            connection.setDialing()
        }
        assertNotNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `an outgoing call without a connection still shows the call`() {
        every { androidCallFramework.connection } returns null

        integrator.onEvent(OutgoingCallStarted(state))

        assertNotNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `a connected call shows the call`() {
        integrator.onEvent(CallConnected(state))

        assertNotNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `an ended call unmutes and tears down the connection`() {
        val cause = slot<DisconnectCause>()

        integrator.onEvent(CallEnded(state))

        verify {
            pil.audio.unmute()
            connection.setDisconnected(capture(cause))
            connection.destroy()
            androidCallFramework.connection = null
        }
        assertEquals(DisconnectCause.REMOTE, cause.captured.code)
    }

    @Test
    fun `other call session events change nothing`() {
        integrator.onEvent(CallStateUpdated(state))

        verify(exactly = 0) { connection.setDialing() }
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `a missed call is notified`() {
        val voipLibCall = mockk<Call> { every { wasMissed } returns true }
        every { factory.make(voipLibCall) } returns call

        integrator.notifyIfMissedCall(voipLibCall)

        assertTrue(missedCallNotificationShown())
    }

    @Test
    fun `answered calls and apps that opt out get no missed call notification`() {
        val answered = mockk<Call> { every { wasMissed } returns false }
        integrator.notifyIfMissedCall(answered)

        val missed = mockk<Call> { every { wasMissed } returns true }
        every { pil.app } returns applicationSetup(notifyOnMissedCall = false)
        integrator.notifyIfMissedCall(missed)

        assertTrue(!missedCallNotificationShown())
    }

    private fun missedCallNotificationShown() =
        shadowOf(application.getSystemService(NotificationManager::class.java))
            .activeNotifications.any { it.id == 781 }
}
