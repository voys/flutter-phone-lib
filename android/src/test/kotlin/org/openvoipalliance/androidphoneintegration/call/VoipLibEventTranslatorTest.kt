package org.openvoipalliance.androidphoneintegration.call

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.CallSessionState
import org.openvoipalliance.androidphoneintegration.android.PlatformIntegrator
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferAborted
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferConnected
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferEnded
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferStarted
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallConnected
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallEnded
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallStateUpdated
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.IncomingCallReceived
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.OutgoingCallStarted
import org.openvoipalliance.androidphoneintegration.events.EventsManager
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.telecom.AndroidCallFramework
import org.openvoipalliance.androidphoneintegration.telecom.Connection
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoipLibEventTranslatorTest {
    private val pil = mockPil()
    private val calls = Calls(mockk(relaxed = true), CallArrayList(Calls.MAX_CALLS))
    private val events = mockk<EventsManager>(relaxed = true)
    private val connection = mockk<Connection>(relaxed = true)
    private val androidCallFramework = mockk<AndroidCallFramework>(relaxed = true)
    private val platformIntegrator = mockk<PlatformIntegrator>(relaxed = true)
    private val sessionState = mockk<CallSessionState>()
    private val translator = VoipLibEventTranslator(pil, calls, events)
    private val call = voipLibCall()
    private val other = voipLibCall()

    @Before
    fun setUp() {
        every { pil.androidCallFramework } returns androidCallFramework
        every { pil.platformIntegrator } returns platformIntegrator
        every { pil.sessionState } returns sessionState
        every { androidCallFramework.connection } returns connection
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `an incoming call is added and announced`() {
        translator.incomingCallReceived(call)

        assertEquals(listOf(call), calls.toList())
        verify { events.broadcast(IncomingCallReceived::class) }
    }

    @Test
    fun `an incoming call is ignored while in a call`() {
        calls.add(other)

        translator.incomingCallReceived(call)

        assertEquals(listOf(other), calls.toList())
        verify(exactly = 0) { events.broadcast(any<kotlin.reflect.KClass<out Event.CallSessionEvent>>()) }
    }

    @Test
    fun `an outgoing call starts a call, or a transfer when already in one`() {
        translator.outgoingCallCreated(call)
        verify { events.broadcast(OutgoingCallStarted::class) }

        translator.outgoingCallCreated(other)
        verify { events.broadcast(AttendedTransferStarted::class) }
        assertEquals(listOf(call, other), calls.toList())
    }

    @Test
    fun `a connected call activates the connection`() {
        calls.add(call)
        translator.callConnected(call)
        verify {
            connection.setActive()
            events.broadcast(CallConnected::class)
        }

        calls.add(other)
        translator.callConnected(other)
        verify { events.broadcast(AttendedTransferConnected::class) }
    }

    @Test
    fun `an ended call is removed and announced with the state from before it ended`() {
        calls.add(call)
        val event = slot<CallEnded>()

        translator.callEnded(call)

        assertTrue(calls.isEmpty())
        verify { events.broadcast(capture(event)) }
        assertSame(sessionState, event.captured.state)
    }

    @Test
    fun `a call ending during a transfer aborts the transfer`() {
        calls.add(call)
        calls.add(other)
        val event = slot<AttendedTransferAborted>()

        translator.callEnded(other)

        assertEquals(listOf(call), calls.toList())
        verify { events.broadcast(capture(event)) }
        assertSame(sessionState, event.captured.state)
    }

    @Test
    fun `a merged transfer removes the call and ends the transfer`() {
        calls.add(call)
        calls.add(other)
        val event = slot<AttendedTransferEnded>()

        translator.attendedTransferMerged(other)

        assertEquals(listOf(call), calls.toList())
        verify { events.broadcast(capture(event)) }
        assertSame(sessionState, event.captured.state)
    }

    @Test
    fun `a released call may be a missed call and prunes the call framework`() {
        translator.callReleased(call)

        verify {
            platformIntegrator.notifyIfMissedCall(call)
            androidCallFramework.prune()
        }
    }

    @Test
    fun `audio is rerouted when streams start or devices change`() {
        translator.streamsStarted()
        translator.availableAudioDevicesUpdated()

        verify(exactly = 2) { connection.updateCurrentRouteBasedOnAudioState() }
    }

    @Test
    fun `other call updates are announced as state updates`() {
        translator.callUpdated(call)
        translator.currentAudioDeviceHasChanged(mockk(relaxed = true))

        verify(exactly = 1) { events.broadcast(CallStateUpdated::class) }
    }
}
