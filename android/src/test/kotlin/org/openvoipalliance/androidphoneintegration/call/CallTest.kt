package org.openvoipalliance.androidphoneintegration.call

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.contacts.Contact
import org.openvoipalliance.androidphoneintegration.pilCall
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallTest {
    private val contact = Contact("Alice Example", null)

    @Test
    fun `the heading prefers the contact, then the display name, then the number`() {
        assertEquals("Alice Example", pilCall(displayName = "Alice", contact = contact).remotePartyHeading)
        assertEquals("Alice", pilCall(displayName = "Alice").remotePartyHeading)
        assertEquals("+31501234567", pilCall(displayName = " ").remotePartyHeading)
    }

    @Test
    fun `the subheading is the number, unless the heading already is`() {
        assertEquals("+31501234567", pilCall(contact = contact).remotePartySubheading)
        assertEquals("+31501234567", pilCall(displayName = "Alice").remotePartySubheading)
        assertEquals("", pilCall().remotePartySubheading)
    }

    @Test
    fun `the pretty remote party combines heading and subheading`() {
        assertEquals("Alice (+31501234567)", pilCall(displayName = "Alice").prettyRemoteParty)
        assertEquals("+31501234567", pilCall().prettyRemoteParty)
    }

    @Test
    fun `the pretty duration is minutes and seconds, with hours when needed`() {
        assertEquals("01:15", pilCall(duration = 75).prettyDuration)
        assertEquals("1:02:05", pilCall(duration = 3725).prettyDuration)
    }
}
