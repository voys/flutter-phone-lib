package org.openvoipalliance.androidphoneintegration.call

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.openvoipalliance.voiplib.model.AttendedTransferSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallsTest {
    private val factory = mockk<CallFactory>()
    private val calls = Calls(factory, CallArrayList(Calls.MAX_CALLS))
    private val first = voipLibCall()
    private val second = voipLibCall()

    @Test
    fun `without calls nothing is active`() {
        every { factory.make(null) } returns null

        assertFalse(calls.isInCall)
        assertFalse(calls.isInTransfer)
        assertNull(calls.activeVoipLibCall)
        assertNull(calls.inactiveVoipLibCall)
        assertNull(calls.transferSession)
        assertNull(calls.active)
        assertNull(calls.inactive)
    }

    @Test
    fun `a single call is active`() {
        val made = pilCall()
        every { factory.make(first) } returns made
        every { factory.make(null) } returns null
        calls.add(first)

        assertTrue(calls.isInCall)
        assertFalse(calls.isInTransfer)
        assertSame(first, calls.activeVoipLibCall)
        assertNull(calls.inactiveVoipLibCall)
        assertNull(calls.transferSession)
        assertEquals(made, calls.active)
        assertNull(calls.inactive)
    }

    @Test
    fun `in a transfer the newest call is active and the first is inactive`() {
        calls.add(first)
        calls.add(second)

        assertTrue(calls.isInTransfer)
        assertSame(second, calls.activeVoipLibCall)
        assertSame(first, calls.inactiveVoipLibCall)
        assertEquals(AttendedTransferSession(from = second, to = first), calls.transferSession)
    }
}
