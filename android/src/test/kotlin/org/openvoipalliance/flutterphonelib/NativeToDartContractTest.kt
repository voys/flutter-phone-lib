package org.openvoipalliance.flutterphonelib

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.CallSessionState
import org.openvoipalliance.androidphoneintegration.contacts.Contact
import org.openvoipalliance.androidphoneintegration.events.Event
import org.openvoipalliance.androidphoneintegration.events.Event.CallSetupFailedEvent.Reason
import org.openvoipalliance.flutterphonelib.contacts.toMap
import org.openvoipalliance.flutterphonelib.events.ProxyEventListener
import org.openvoipalliance.flutterphonelib.events.toMap
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Asserts that the bridge produces exactly `android_to_dart.json`, which the Dart tests decode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NativeToDartContractTest {
    private val contract = Contract.androidToDart

    @After
    fun tearDown() {
        PhoneLib.channel = null
    }

    @Test
    fun `session state matches the contract`() {
        assertEquals(
            normalized(contract["sessionState"]),
            normalized(overTheWire(Fixtures.sessionState().toMap())),
        )
    }

    @Test
    fun `idle session state matches the contract`() {
        assertEquals(
            normalized(contract["idleSessionState"]),
            normalized(overTheWire(Fixtures.idleSessionState().toMap())),
        )
    }

    @Test
    fun `the contract lists every call session event`() {
        assertEquals(
            (contract["callSessionEventTypes"] as List<*>).toSet(),
            concreteEvents(Event.CallSessionEvent::class.java).map { it.simpleName }.toSet(),
        )
    }

    @Test
    fun `call session events match the contract`() {
        for (eventClass in concreteEvents(Event.CallSessionEvent::class.java)) {
            val event = eventClass.getConstructor(CallSessionState::class.java)
                .newInstance(Fixtures.sessionState())

            assertEquals(
                eventClass.simpleName,
                normalized(mapOf("type" to eventClass.simpleName, "state" to contract["sessionState"])),
                normalized(overTheWire(event.toMap())),
            )
        }
    }

    @Test
    fun `the contract lists every call setup failed event and reason`() {
        assertEquals(
            (contract["callSetupFailedEventTypes"] as List<*>).toSet(),
            concreteEvents(Event.CallSetupFailedEvent::class.java).map { it.simpleName }.toSet(),
        )
        assertEquals(contract["callSetupFailedReasons"], Reason.values().map { it.name })
    }

    @Test
    fun `call setup failed events match the contract`() {
        for (eventClass in concreteEvents(Event.CallSetupFailedEvent::class.java)) {
            for (reason in Reason.values()) {
                val event = eventClass.getConstructor(Reason::class.java).newInstance(reason)

                assertEquals(
                    mapOf("type" to eventClass.simpleName, "reason" to reason.name),
                    overTheWire(event.toMap()),
                )
            }
        }
    }

    @Test
    fun `ProxyEventListener sends events to Dart as onEvent`() {
        val messenger = RecordingMessenger()
        PhoneLib.channel = messenger.channel()

        ProxyEventListener().onEvent(Event.CallSessionEvent.CallConnected(Fixtures.sessionState()))

        val call = messenger.sent.single()
        assertEquals("onEvent", call.method)
        assertEquals(
            normalized(mapOf("type" to "CallConnected", "state" to contract["sessionState"])),
            normalized(call.arguments),
        )
    }

    @Test
    fun `ProxyEventListener does nothing without a channel`() {
        PhoneLib.channel = null

        ProxyEventListener().onEvent(Event.CallSessionEvent.CallConnected(Fixtures.sessionState()))
    }

    // Dart's Contact.fromJson keeps this as Uri("null"); see test/models_test.dart.
    @Test
    fun `a contact without a photo sends the string null`() {
        assertEquals("null", Contact("Bob", null).toMap()["imageUri"])
    }

    private fun concreteEvents(root: Class<out Event>): List<Class<out Event>> =
        root.declaredClasses.filter { Event::class.java.isAssignableFrom(it) }.flatMap { nested ->
            @Suppress("UNCHECKED_CAST")
            val eventClass = nested as Class<out Event>
            if (java.lang.reflect.Modifier.isAbstract(nested.modifiers)) {
                concreteEvents(eventClass)
            } else {
                listOf(eventClass)
            }
        }
}
