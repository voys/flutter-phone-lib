package org.openvoipalliance.androidphoneintegration.contacts

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContactsTest {
    private val contentResolver = mockk<ContentResolver>()
    private val context = mockk<Context> {
        every { checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { contentResolver } returns this@ContactsTest.contentResolver
    }
    private val contacts = Contacts(context)
    private val call = voipLibCall()
    private val lookup = slot<Uri>()

    @Before
    fun setUp() {
        every { call.linphoneCall.remoteAddress.username } returns "+31 50 123"
        returnsRow("Alice Example", "content://com.android.contacts/contacts/1/photo")
    }

    @Test
    fun `a contact is found by phone number`() {
        assertEquals(
            Contact("Alice Example", Uri.parse("content://com.android.contacts/contacts/1/photo")),
            contacts.find(call),
        )
        assertEquals(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode("+31 50 123")),
            lookup.captured,
        )
    }

    @Test
    fun `a contact without a photo has no image`() {
        returnsRow("Alice Example", "")

        assertEquals(Contact("Alice Example", null), contacts.find(call))
    }

    @Test
    fun `a contact without a name is not a contact`() {
        returnsRow(" ", null)

        assertNull(contacts.find(call))
    }

    @Test
    fun `an unknown number has no contact`() {
        every { contentResolver.query(capture(lookup), any(), any(), any(), any()) } returns
            MatrixCursor(arrayOf("display_name", "photo_uri"))

        assertNull(contacts.find(call))
    }

    @Test
    fun `no contact is found when the query fails`() {
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        assertNull(contacts.find(call))
    }

    @Test
    fun `contacts aren't searched without permission`() {
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED

        assertNull(contacts.find(call))
        verify(exactly = 0) { contentResolver.query(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `each call is only searched once, even without a result`() {
        contacts.find(call)
        contacts.find(call)

        val unknown = voipLibCall()
        every { unknown.linphoneCall.remoteAddress.username } returns "+31 50 456"
        every { contentResolver.query(match { it != lookup.captured }, any(), any(), any(), any()) } returns null
        contacts.find(unknown)
        contacts.find(unknown)

        verify(exactly = 2) { contentResolver.query(any(), any(), any(), any(), any()) }
    }

    private fun returnsRow(name: String, photo: String?) {
        every { contentResolver.query(capture(lookup), any(), any(), any(), any()) } answers {
            MatrixCursor(arrayOf("display_name", "photo_uri")).apply { addRow(arrayOf(name, photo)) }
        }
    }
}
