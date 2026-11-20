package org.openvoipalliance.androidphoneintegration.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.TestIncomingCallActivity
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.contacts.Contact
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.androidphoneintegration.service.NotificationButtonReceiver
import org.openvoipalliance.flutterphonelib.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IncomingCallNotificationTest {
    private val pil = mockPil()
    private val ringer = mockk<IncomingCallRinger>(relaxed = true)
    private lateinit var notification: IncomingCallNotification
    private val call = pilCall(
        displayName = "Alice",
        contact = Contact("Alice Example", Uri.parse("content://contacts/1/photo")),
    )

    @Before
    fun setUp() {
        installNotificationKoin(pil)
        every { pil.calls.isInCall } returns true
        notification = IncomingCallNotification(ringer)
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `notifying posts the notification on a loud, silent channel and rings`() {
        notification.notify(call)

        val channel = notificationManager.getNotificationChannel("Incoming Calls")
        assertEquals(application.getString(R.string.notification_incoming_calls_channel_name), channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.canShowBadge())

        val posted = postedNotification(2435)
        assertEquals("Incoming Calls", posted.channelId)
        assertTrue(posted.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0)
        verify { ringer.start() }
    }

    @Test
    fun `silencing re-posts the notification without alerting and stops ringing`() {
        notification.silence(call)

        assertTrue(postedNotification(2435).flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        verify { ringer.stop() }
    }

    @Test
    fun `it is an incoming call style notification with answer and decline buttons`() {
        val built = notification.build(call)

        assertEquals(Notification.CallStyle::class.java.name, built.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals(Notification.CATEGORY_CALL, built.category)
        assertButtonPress(NotificationButtonReceiver.Action.ANSWER, built.extras.getParcelable(Notification.EXTRA_ANSWER_INTENT))
        assertButtonPress(NotificationButtonReceiver.Action.DECLINE, built.extras.getParcelable(Notification.EXTRA_DECLINE_INTENT))
    }

    @Test
    fun `the full screen intent opens the incoming call screen with the caller`() {
        val intent = notification.build(call).fullScreenIntent.intent

        assertEquals(ComponentName(application, TestIncomingCallActivity::class.java), intent.component)
        assertTrue(intent.getBooleanExtra("is_incoming", false))
        assertEquals("Alice Example", intent.getStringExtra("remote_party_heading"))
        assertEquals("+31501234567", intent.getStringExtra("remote_party_subheading"))
        assertEquals("content://contacts/1/photo", intent.getStringExtra("remote_party_image_uri"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NO_USER_ACTION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `a caller without a photo sends no image`() {
        val intent = notification.build(pilCall()).fullScreenIntent.intent

        assertFalse(intent.hasExtra("remote_party_image_uri"))
    }

    @Test
    fun `cancelling removes the notification, stops ringing and closes the incoming call screen`() {
        notification.notify(call)

        notification.cancel()

        assertEquals(0, notificationManager.activeNotifications.size)
        verify { ringer.stop() }
        assertEquals(
            "org.openvoipalliance.androidphoneintegration.INCOMING_CALL_CANCEL",
            shadowOf(application).broadcastIntents.first().action,
        )
    }
}
