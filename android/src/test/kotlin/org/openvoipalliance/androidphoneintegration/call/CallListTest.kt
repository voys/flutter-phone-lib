package org.openvoipalliance.androidphoneintegration.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallListTest {
    private val list = CallArrayList(maxCalls = 2)

    @Test
    fun `a call is only added once`() {
        val call = voipLibCall()

        assertTrue(list.addCall(call))
        assertFalse(list.addCall(VoipLibCall(call.linphoneCall)))
        assertEquals(1, list.size)
    }

    // `size > maxCalls` is checked before adding, so one more than the maximum fits.
    @Test
    fun `one call more than the maximum can be added`() {
        assertTrue(list.addCall(voipLibCall()))
        assertTrue(list.addCall(voipLibCall()))
        assertTrue(list.addCall(voipLibCall()))
        assertFalse(list.addCall(voipLibCall()))
        assertEquals(3, list.size)
    }

    @Test
    fun `calls are found and removed by their linphone call`() {
        val call = voipLibCall()
        val other = voipLibCall()
        list.addCall(call)
        val sameCall = VoipLibCall(call.linphoneCall)

        assertTrue(list.callExists(sameCall))
        assertFalse(list.callExists(other))
        assertSame(call, list.findCall(sameCall))
        assertNull(list.findCall(other))

        assertNull(list.removeCall(other))
        assertSame(call, list.removeCall(sameCall))
        assertTrue(list.isEmpty())
    }
}
