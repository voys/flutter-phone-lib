package org.openvoipalliance.androidphoneintegration.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.Person
import android.content.ComponentName
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.TestCallActivity
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.contacts.Contact
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.androidphoneintegration.service.NotificationButtonReceiver
import org.openvoipalliance.flutterphonelib.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallNotificationTest {
    private val pil = mockPil()
    private lateinit var notification: CallNotification
    private val call = pilCall(displayName = "Alice", duration = 75)

    @Before
    fun setUp() {
        installNotificationKoin(pil)
        notification = CallNotification()
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `the channel is quiet and has no badge`() {
        notification.createNotificationChannel()

        val channel = notificationManager.getNotificationChannel("VoIP")
        assertEquals(application.getString(R.string.notification_channel_name), channel.name)
        assertEquals(NotificationManager.IMPORTANCE_MIN, channel.importance)
        assertFalse(channel.canShowBadge())
    }

    @Test
    fun `without a call it is a default ongoing notification with a hang up button`() {
        val built = notification.build()

        assertEquals("VoIP", built.channelId)
        assertEquals(application.getString(R.string.notification_default_title), built.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(application.getString(R.string.notification_default_subtitle), built.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notification.CATEGORY_SERVICE, built.category)
        assertTrue(built.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(ComponentName(application, TestCallActivity::class.java), built.contentIntent.intent.component)

        val hangUp = built.actions.single()
        assertEquals(application.getString(R.string.notification_hang_up_action), hangUp.title)
        assertButtonPress(NotificationButtonReceiver.Action.HANG_UP, hangUp.actionIntent)
    }

    @Test
    fun `with a call it is an ongoing call style notification`() {
        val before = System.currentTimeMillis()

        val built = notification.build(call)

        assertEquals(Notification.CallStyle::class.java.name, built.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(Notification.CATEGORY_CALL, built.category)
        assertTrue(built.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(built.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(ComponentName(application, TestCallActivity::class.java), built.contentIntent.intent.component)
        assertEquals("Alice (+31501234567)", person(built).name)
        assertButtonPress(
            NotificationButtonReceiver.Action.HANG_UP,
            built.extras.getParcelable(Notification.EXTRA_HANG_UP_INTENT),
        )
        // The chronometer starts at the call's start: its duration in seconds, as milliseconds.
        assertTrue(built.`when` in (before - 75_000)..(System.currentTimeMillis() - 75_000))
    }

    @Test
    fun `durationInSeconds is the duration in milliseconds`() {
        assertEquals(75_000, call.durationInSeconds)
    }

    @Test
    fun `a call becomes an important person with the contact's photo`() {
        val withPhoto = pilCall(contact = Contact("Alice Example", Uri.parse("content://contacts/1/photo")))

        val person = withPhoto.toPerson()

        assertEquals("Alice Example (+31501234567)", person.name)
        assertTrue(person.isImportant)
        assertNotNull(person.icon)
        assertNull(call.toPerson().icon)
    }

    @Test
    fun `cancelling removes the notification`() {
        notificationManager.notify(notification.notificationId, notification.build())

        notification.cancel()

        assertEquals(0, notificationManager.activeNotifications.size)
    }

    private fun person(notification: Notification): Person =
        notification.extras.getParcelable(Notification.EXTRA_CALL_PERSON, Person::class.java)!!
}
