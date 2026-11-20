package org.openvoipalliance.androidphoneintegration.notifications

import android.app.Notification
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.androidphoneintegration.service.NotificationButtonReceiver
import org.openvoipalliance.flutterphonelib.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Before Android 12 there is no CallStyle, so calls get a regular notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LegacyNotificationTest {
    private val pil = mockPil()
    private val call = pilCall(displayName = "Alice", duration = 75)

    @Before
    fun setUp() = installNotificationKoin(pil)

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `the ongoing call notification shows the caller and duration`() {
        val built = CallNotification().build(call)

        assertNull(built.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals("Alice", built.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("01:15", built.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(built.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertButtonPress(NotificationButtonReceiver.Action.HANG_UP, built.actions.single().actionIntent)
    }

    @Test
    fun `the incoming call notification insists, with answer and decline buttons`() {
        val built = IncomingCallNotification(mockk(relaxed = true)).build(call)

        assertEquals("Alice (+31501234567)", built.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            application.getString(R.string.notification_incoming_context_text),
            built.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(Notification.CATEGORY_CALL, built.category)
        assertEquals(Notification.VISIBILITY_PUBLIC, built.visibility)
        assertTrue(built.flags and Notification.FLAG_INSISTENT != 0)
        assertTrue(built.fullScreenIntent != null)

        val (answer, decline) = built.actions.toList()
        assertEquals(application.getString(R.string.notification_answer_action), answer.title.toString())
        assertButtonPress(NotificationButtonReceiver.Action.ANSWER, answer.actionIntent)
        assertEquals(application.getString(R.string.notification_decline_action), decline.title.toString())
        assertButtonPress(NotificationButtonReceiver.Action.DECLINE, decline.actionIntent)
    }
}
