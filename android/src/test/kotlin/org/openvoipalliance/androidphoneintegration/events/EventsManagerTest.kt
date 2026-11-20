package org.openvoipalliance.androidphoneintegration.events

import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.CallSessionState
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferAborted
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferConnected
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferEnded
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AttendedTransferEvent.AttendedTransferStarted
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AudioStateUpdated
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallConnected
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallDurationUpdated
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallEnded
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallReleased
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.CallStateUpdated
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.IncomingCallReceived
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.OutgoingCallStarted
import org.openvoipalliance.androidphoneintegration.mockPil
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EventsManagerTest {
    private val pil = mockPil()
    private val state = mockk<CallSessionState>()
    private val events = EventsManager(pil)
    private val received = mutableListOf<Event>()
    private val listener = object : PILEventListener {
        override fun onEvent(event: Event) {
            received += event
        }
    }

    @Before
    fun setUp() {
        every { pil.isStarted } returns true
        every { pil.sessionState } returns state
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `listeners receive broadcasts on the main thread`() {
        val event = CallConnected(state)
        events.listen(listener)

        events.broadcast(event)
        assertTrue(received.isEmpty())

        idleMainLooper()
        assertEquals(listOf<Event>(event), received)
    }

    @Test
    fun `a listener is only added once and can stop listening`() {
        events.listen(listener)
        events.listen(listener)
        events.broadcast(CallConnected(state))
        idleMainLooper()
        assertEquals(1, received.size)

        events.stopListening(listener)
        events.broadcast(CallConnected(state))
        idleMainLooper()
        assertEquals(1, received.size)
    }

    @Test
    fun `nothing is broadcast until the PIL is started`() {
        every { pil.isStarted } returns false
        events.listen(listener)

        events.broadcast(CallConnected(state))
        idleMainLooper()

        assertTrue(received.isEmpty())
    }

    @Test
    fun `call session event classes are broadcast with the current session state`() {
        val classes = listOf(
            OutgoingCallStarted::class,
            IncomingCallReceived::class,
            CallEnded::class,
            CallReleased::class,
            CallConnected::class,
            CallDurationUpdated::class,
            AudioStateUpdated::class,
            CallStateUpdated::class,
            AttendedTransferStarted::class,
            AttendedTransferConnected::class,
            AttendedTransferAborted::class,
            AttendedTransferEnded::class,
        )
        events.listen(listener)

        classes.forEach { events.broadcast(it) }
        idleMainLooper()

        assertEquals(classes, received.map { it::class })
        received.forEach { assertSame(state, (it as Event.CallSessionEvent).state) }
    }

    @Test
    fun `an abstract event class can't be broadcast`() {
        assertThrows(IllegalArgumentException::class.java) {
            events.broadcast(Event.CallSessionEvent::class)
        }
    }

    // They extend CallSessionEvent directly, so `is AttendedTransferEvent` never matches.
    @Test
    fun `attended transfer events are not AttendedTransferEvents`() {
        val transferEvents: List<Event> = listOf(
            AttendedTransferStarted(state),
            AttendedTransferConnected(state),
            AttendedTransferAborted(state),
            AttendedTransferEnded(state),
        )

        transferEvents.forEach { assertFalse(it is AttendedTransferEvent) }
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()
}
