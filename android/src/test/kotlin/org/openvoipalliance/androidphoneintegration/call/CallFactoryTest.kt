package org.openvoipalliance.androidphoneintegration.call

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.contacts.Contact
import org.openvoipalliance.androidphoneintegration.contacts.Contacts
import org.openvoipalliance.voiplib.model.Direction
import org.openvoipalliance.voiplib.model.Quality
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.openvoipalliance.voiplib.model.CallState as VoipLibCallState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallFactoryTest {
    private val contacts = mockk<Contacts>()
    private val factory = CallFactory(contacts)
    private val voipLibCall = mockk<VoipLibCall>()

    @Before
    fun setUp() {
        every { voipLibCall.pAssertedIdentity } returns ""
        every { voipLibCall.remotePartyId } returns ""
        every { voipLibCall.displayName } returns "Alice"
        every { voipLibCall.phoneNumber } returns "+31501234567"
        every { voipLibCall.state } returns VoipLibCallState.StreamsRunning
        every { voipLibCall.direction } returns Direction.OUTGOING
        every { voipLibCall.duration } returns 75
        every { voipLibCall.isOnHold } returns false
        every { voipLibCall.quality } returns Quality(4.5f, 4.25f)
        every { voipLibCall.callId } returns "call-id"
        every { voipLibCall.reason } returns "None"
        every { contacts.find(voipLibCall) } returns null
    }

    @Test
    fun `no call makes no call`() {
        assertNull(factory.make(null))
    }

    @Test
    fun `a call is made from the voip lib call`() {
        val contact = Contact("Alice Example", null)
        every { contacts.find(voipLibCall) } returns contact

        val call = factory.make(voipLibCall)!!

        assertEquals("+31501234567", call.remoteNumber)
        assertEquals("Alice", call.displayName)
        assertEquals(CallState.CONNECTED, call.state)
        assertEquals(CallDirection.OUTBOUND, call.direction)
        assertEquals(75, call.duration)
        assertEquals(false, call.isOnHold)
        assertEquals(4.5f, call.mos)
        assertEquals(4.25f, call.currentMos)
        assertEquals(contact, call.contact)
        assertEquals("call-id", call.callId)
        assertEquals("None", call.reason)
    }

    @Test
    fun `each call made gets a new uuid`() {
        assertNotEquals(factory.make(voipLibCall)!!.uuid, factory.make(voipLibCall)!!.uuid)
    }

    @Test
    fun `an incoming call is inbound`() {
        every { voipLibCall.direction } returns Direction.INCOMING

        assertEquals(CallDirection.INBOUND, factory.make(voipLibCall)!!.direction)
    }

    @Test
    fun `voip lib states map to call states`() {
        val expected = mapOf(
            CallState.INITIALIZING to listOf(VoipLibCallState.Idle, VoipLibCallState.OutgoingInit),
            CallState.RINGING to listOf(
                VoipLibCallState.IncomingReceived,
                VoipLibCallState.PushIncomingReceived,
                VoipLibCallState.OutgoingProgress,
                VoipLibCallState.OutgoingRinging,
            ),
            CallState.HELD_BY_LOCAL to listOf(VoipLibCallState.Pausing, VoipLibCallState.Paused),
            CallState.HELD_BY_REMOTE to listOf(VoipLibCallState.PausedByRemote),
            CallState.CONNECTED to listOf(
                VoipLibCallState.OutgoingEarlyMedia,
                VoipLibCallState.Connected,
                VoipLibCallState.StreamsRunning,
                VoipLibCallState.Referred,
                VoipLibCallState.UpdatedByRemote,
                VoipLibCallState.IncomingEarlyMedia,
                VoipLibCallState.Updating,
                VoipLibCallState.EarlyUpdatedByRemote,
                VoipLibCallState.EarlyUpdating,
                VoipLibCallState.Resuming,
            ),
            CallState.ERROR to listOf(VoipLibCallState.Error, VoipLibCallState.Unknown),
            CallState.ENDED to listOf(VoipLibCallState.End, VoipLibCallState.Released),
        )
        assertEquals(VoipLibCallState.values().toSet(), expected.values.flatten().toSet())

        for ((state, voipLibStates) in expected) {
            for (voipLibState in voipLibStates) {
                every { voipLibCall.state } returns voipLibState

                assertEquals(voipLibState.name, state, factory.make(voipLibCall)!!.state)
            }
        }
    }

    @Test
    fun `the P-Asserted-Identity header names the remote party first`() {
        every { voipLibCall.pAssertedIdentity } returns "\"Bob\" <sip:202@sip.example.com>"
        every { voipLibCall.remotePartyId } returns "\"Carol\" <sip:203@sip.example.com>"

        val call = factory.make(voipLibCall)!!

        assertEquals("Bob", call.displayName)
        assertEquals("202", call.remoteNumber)
    }

    @Test
    fun `the Remote-Party-ID header is used without a P-Asserted-Identity`() {
        every { voipLibCall.remotePartyId } returns "\"Carol\" sip:203@sip.example.com"

        val call = factory.make(voipLibCall)!!

        assertEquals("Carol", call.displayName)
        assertEquals("203", call.remoteNumber)
    }

    @Test
    fun `headers that can't be parsed are ignored`() {
        every { voipLibCall.pAssertedIdentity } returns "<sip:202@sip.example.com>"
        every { voipLibCall.remotePartyId } returns "garbage"

        val call = factory.make(voipLibCall)!!

        assertEquals("Alice", call.displayName)
        assertEquals("+31501234567", call.remoteNumber)
    }

    @Test
    fun `capture groups are extracted in order`() {
        assertEquals(listOf("a", "b"), "a-b".extractCaptureGroups("(.)-(.)"))
        assertEquals(emptyList<String>(), "ab".extractCaptureGroups("(.)-(.)"))
        assertEquals(emptyList<String>(), "a-b".extractCaptureGroups(".-."))
    }
}
