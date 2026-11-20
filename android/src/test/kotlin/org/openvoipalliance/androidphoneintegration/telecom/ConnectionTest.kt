package org.openvoipalliance.androidphoneintegration.telecom

import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.Reason
import org.openvoipalliance.androidphoneintegration.call.CallArrayList
import org.openvoipalliance.androidphoneintegration.call.Calls
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AudioStateUpdated
import org.openvoipalliance.androidphoneintegration.events.EventsManager
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.notifications.IncomingCallNotification
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.openvoipalliance.voiplib.Actions
import org.openvoipalliance.voiplib.VoIPLib
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.telecom.Connection as AndroidConnection
import org.linphone.core.Call as LinphoneCall

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectionTest {
    private val pil = mockPil()
    private val voipLib = mockk<VoIPLib>()
    private val incomingCallNotification = mockk<IncomingCallNotification>(relaxed = true)
    private val calls = Calls(mockk(relaxed = true), CallArrayList(Calls.MAX_CALLS))
    private val events = mockk<EventsManager>(relaxed = true)
    private val connection = Connection(pil, voipLib, incomingCallNotification, calls)
    private val call = voipLibCall()
    private val actions = mockk<Actions>(relaxed = true)
    private val pilCall = pilCall()

    @Before
    fun setUp() {
        every { voipLib.actions(call) } returns actions
        every { pil.events } returns events
        every { pil.calls.active } returns pilCall
        calls.add(call)
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `the incoming call UI is the incoming call notification`() {
        connection.onShowIncomingCallUi()
        verify { incomingCallNotification.notify(pilCall) }

        connection.onSilence()
        verify { incomingCallNotification.silence(pilCall) }
    }

    @Test
    fun `the incoming call UI needs an active call`() {
        every { pil.calls.active } returns null

        connection.onShowIncomingCallUi()
        connection.onSilence()

        verify(exactly = 0) {
            incomingCallNotification.notify(any(), any())
            incomingCallNotification.silence(any())
        }
    }

    @Test
    fun `holding and unholding update the call and the connection`() {
        connection.onHold()
        verify { actions.hold(true) }
        assertEquals(AndroidConnection.STATE_HOLDING, connection.state)

        connection.onUnhold()
        verify { actions.hold(false) }
        assertEquals(AndroidConnection.STATE_ACTIVE, connection.state)
    }

    @Test
    fun `toggling hold follows the call's hold state`() {
        every { call.linphoneCall.state } returns LinphoneCall.State.StreamsRunning
        connection.toggleHold()
        verify { actions.hold(true) }

        every { call.linphoneCall.state } returns LinphoneCall.State.Paused
        connection.toggleHold()
        verify { actions.hold(false) }
    }

    @Test
    fun `answering accepts the call`() {
        connection.onAnswer()

        verify { actions.accept() }
    }

    @Test
    fun `rejecting declines the call as busy`() {
        connection.onReject()

        verify { actions.decline(Reason.Busy) }
        assertEquals(AndroidConnection.STATE_DISCONNECTED, connection.state)
        assertEquals(DisconnectCause.BUSY, connection.disconnectCause.code)
    }

    @Test
    fun `disconnecting ends the call`() {
        connection.onDisconnect()

        verify { actions.end() }
        assertEquals(AndroidConnection.STATE_NEW, connection.state)
    }

    @Test
    fun `actions need a call`() {
        calls.removeCall(call)

        connection.onHold()
        connection.onAnswer()
        connection.onDisconnect()

        verify(exactly = 0) { voipLib.actions(any()) }
    }

    @Test
    fun `leaving the ringing state cancels the incoming call notification`() {
        connection.setRinging()
        verify(exactly = 0) { incomingCallNotification.cancel() }

        connection.setActive()
        verify { incomingCallNotification.cancel() }
        assertEquals(AndroidConnection.STATE_ACTIVE, connection.state)
    }

    @Test
    fun `a state change without calls disconnects the connection`() {
        calls.removeCall(call)

        connection.setRinging()

        assertEquals(AndroidConnection.STATE_DISCONNECTED, connection.state)
        assertEquals(DisconnectCause.UNKNOWN, connection.disconnectCause.code)
    }

    @Test
    fun `audio follows the telecom route`() {
        val routes = mapOf(
            CallAudioState.ROUTE_EARPIECE to { verify { actions.routeAudioToEarpiece(call) } },
            CallAudioState.ROUTE_SPEAKER to { verify { actions.routeAudioToSpeaker(call) } },
            CallAudioState.ROUTE_BLUETOOTH to { verify { actions.routeAudioToBluetooth(call) } },
            CallAudioState.ROUTE_WIRED_HEADSET to { verify { actions.routeAudioToHeadset(call) } },
        )

        for ((route, verifyRouted) in routes) {
            connection.onCallAudioStateChanged(CallAudioState(false, route, route))
            verifyRouted()
        }
        verify(exactly = routes.size) { events.broadcast(AudioStateUpdated::class) }
    }

    @Test
    fun `audio goes to the earpiece for any other route`() {
        connection.onCallAudioStateChanged(CallAudioState(false, CallAudioState.ROUTE_WIRED_OR_EARPIECE, 5))
        connection.updateCurrentRouteBasedOnAudioState()

        verify(exactly = 2) { actions.routeAudioToEarpiece(call) }
    }

    @Test
    fun `audio isn't routed without a call`() {
        calls.removeCall(call)

        connection.onCallAudioStateChanged(CallAudioState(false, CallAudioState.ROUTE_SPEAKER, 8))

        verify(exactly = 0) { voipLib.actions(any()) }
        verify(exactly = 0) { events.broadcast(AudioStateUpdated::class) }
    }
}
