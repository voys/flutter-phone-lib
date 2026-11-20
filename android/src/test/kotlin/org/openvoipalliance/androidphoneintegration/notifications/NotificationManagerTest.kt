package org.openvoipalliance.androidphoneintegration.notifications

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.mockPil
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationManagerTest {
    private val pil = mockPil()
    private val incomingCallNotification = mockk<IncomingCallNotification>(relaxed = true)
    private val notifications = NotificationManager(pil, incomingCallNotification)

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `the incoming call notification is stale once there are no calls`() {
        every { pil.calls.isInCall } returns false

        notifications.dismissStale()

        verify { incomingCallNotification.cancel() }
    }

    @Test
    fun `the incoming call notification stays during a call`() {
        every { pil.calls.isInCall } returns true

        notifications.dismissStale()

        verify(exactly = 0) { incomingCallNotification.cancel() }
    }
}
