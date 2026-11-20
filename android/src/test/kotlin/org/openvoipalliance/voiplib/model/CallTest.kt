package org.openvoipalliance.voiplib.model

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.Reason
import org.openvoipalliance.voiplib.repository.PreservedInviteData
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.linphone.core.Call as LinphoneCall

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallTest {
    private val linphoneCall = mockk<LinphoneCall>(relaxed = true)
    private val call = Call(linphoneCall)

    @Test
    fun `state matches the linphone state by name`() {
        for (state in LinphoneCall.State.values()) {
            every { linphoneCall.state } returns state

            assertEquals(state.name, call.state.name)
        }
    }

    @Test
    fun `quality reports the average and current MOS`() {
        every { linphoneCall.averageQuality } returns 4.1f
        every { linphoneCall.currentQuality } returns 3.2f

        assertEquals(Quality(4.1f, 3.2f), call.quality)
    }

    @Test
    fun `display name and phone number come from the remote address`() {
        every { linphoneCall.remoteAddress.displayName } returns "Alice"
        every { linphoneCall.remoteAddress.username } returns "+31501234567"

        assertEquals("Alice", call.displayName)
        assertEquals("+31501234567", call.phoneNumber)
    }

    @Test
    fun `display name and phone number are empty when the remote address lacks them`() {
        every { linphoneCall.remoteAddress.displayName } returns null
        every { linphoneCall.remoteAddress.username } returns null

        assertEquals("", call.displayName)
        assertEquals("", call.phoneNumber)
    }

    @Test
    fun `an incoming call that was missed, aborted or early aborted was missed`() {
        every { linphoneCall.callLog.dir } returns LinphoneCall.Dir.Incoming

        for (status in LinphoneCall.Status.values()) {
            every { linphoneCall.callLog.status } returns status

            val missed = status in listOf(
                LinphoneCall.Status.Missed,
                LinphoneCall.Status.Aborted,
                LinphoneCall.Status.EarlyAborted,
            )
            assertEquals(status.name, missed, call.wasMissed)
        }
    }

    @Test
    fun `an outgoing call is never missed`() {
        every { linphoneCall.callLog.dir } returns LinphoneCall.Dir.Outgoing
        every { linphoneCall.callLog.status } returns LinphoneCall.Status.Missed

        assertFalse(call.wasMissed)
    }

    @Test
    fun `a call without a call log was not missed and has an unknown call id`() {
        every { linphoneCall.callLog } throws NullPointerException()

        assertFalse(call.wasMissed)
        assertEquals("unknown", call.callId)
    }

    @Test
    fun `call id comes from the call log`() {
        every { linphoneCall.callLog.callId } returns "abc@sip.example.com"
        assertEquals("abc@sip.example.com", call.callId)

        every { linphoneCall.callLog.callId } returns null
        assertEquals("", call.callId)
    }

    @Test
    fun `reason is the linphone reason name`() {
        every { linphoneCall.reason } returns Reason.Busy

        assertEquals("Busy", call.reason)
    }

    @Test
    fun `direction follows the call log`() {
        every { linphoneCall.callLog.dir } returns LinphoneCall.Dir.Outgoing
        assertEquals(Direction.OUTGOING, call.direction)

        every { linphoneCall.callLog.dir } returns LinphoneCall.Dir.Incoming
        assertEquals(Direction.INCOMING, call.direction)
    }

    @Test
    fun `invite headers come from the preserved invite data`() {
        every { linphoneCall.userData } returns PreservedInviteData().apply {
            remotePartyId = "\"Alice\" <sip:201@sip.example.com>"
            pAssertedIdentity = "\"Bob\" <sip:202@sip.example.com>"
        }

        assertEquals("\"Alice\" <sip:201@sip.example.com>", call.remotePartyId)
        assertEquals("\"Bob\" <sip:202@sip.example.com>", call.pAssertedIdentity)
    }

    @Test
    fun `invite headers are empty without preserved invite data`() {
        every { linphoneCall.userData } returns null
        assertEquals("", call.remotePartyId)
        assertEquals("", call.pAssertedIdentity)

        every { linphoneCall.userData } returns "something else"
        assertEquals("", call.remotePartyId)
        assertEquals("", call.pAssertedIdentity)
    }

    @Test
    fun `only a paused call is on hold`() {
        for (state in LinphoneCall.State.values()) {
            every { linphoneCall.state } returns state

            assertEquals(state.name, state == LinphoneCall.State.Paused, call.isOnHold)
        }
    }

    @Test
    fun `duration comes from linphone`() {
        every { linphoneCall.duration } returns 42

        assertEquals(42, call.duration)
        assertTrue(call.linphoneCall === linphoneCall)
    }
}
