package org.openvoipalliance.androidphoneintegration.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.TestCallActivity
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.applicationSetup
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.flutterphonelib.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MissedCallNotificationTest {
    private val onPressed = PendingIntent.getActivity(
        application, 0, Intent(application, TestCallActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
    private val pil = mockPil(applicationSetup().copy(onMissedCallNotificationPressed = onPressed))
    private lateinit var notification: MissedCallNotification

    @Before
    fun setUp() {
        installNotificationKoin(pil)
        notification = MissedCallNotification()
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `a missed call names the caller`() {
        notification.notify(pilCall(displayName = "Alice"))

        val posted = postedNotification(781)
        assertEquals(title(1), posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(subtitle(1, "Alice (+31501234567)"), posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("MissedCalls", posted.channelId)
        assertEquals(Notification.CATEGORY_STATUS, posted.category)
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertSame(onPressed, posted.contentIntent)
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            notificationManager.getNotificationChannel("MissedCalls").importance,
        )
    }

    @Test
    fun `further missed calls are counted while the notification is shown`() {
        notification.notify(pilCall(displayName = "Alice"))
        notification.notify(pilCall(displayName = "Bob"))
        notification.notify(pilCall(displayName = "Carol"))

        val posted = postedNotification(781)
        assertEquals(title(3), posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(subtitle(3, 3), posted.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `the count starts again once the notification is gone`() {
        notification.notify(pilCall(displayName = "Alice"))
        notification.notify(pilCall(displayName = "Bob"))
        notification.cancel()

        notification.notify(pilCall(displayName = "Carol"))

        assertEquals(title(1), postedNotification(781).extras.getString(Notification.EXTRA_TITLE))
    }

    private fun title(count: Int) =
        application.resources.getQuantityString(R.plurals.notification_missed_call_title, count)

    private fun subtitle(count: Int, argument: Any) =
        application.resources.getQuantityString(R.plurals.notification_missed_call_subtitle, count, argument)
}
