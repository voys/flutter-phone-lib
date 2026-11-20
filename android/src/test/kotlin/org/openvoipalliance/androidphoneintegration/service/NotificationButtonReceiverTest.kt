package org.openvoipalliance.androidphoneintegration.service

import android.content.ComponentName
import android.content.Intent
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.mockPil
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationButtonReceiverTest {
    private val receiver = NotificationButtonReceiver()

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `each button performs its call action from the VoIP service`() {
        val pil = mockPil()

        receive("HANG_UP")
        receive("ANSWER")
        receive("DECLINE")

        verify(exactly = 3) { pil.notifications.dismissStale() }
        verify {
            pil.actions.end()
            pil.actions.answer()
            pil.actions.decline()
        }
        repeat(3) {
            assertEquals(
                ComponentName(application, VoIPService::class.java),
                shadowOf(application).nextStartedService.component,
            )
        }
    }

    @Test
    fun `unknown buttons do nothing`() {
        val pil = mockPil()

        receive("SOMETHING_ELSE")
        receiver.onReceive(application, Intent())

        verify(exactly = 0) {
            pil.actions.end()
            pil.actions.answer()
            pil.actions.decline()
        }
    }

    @Test
    fun `buttons do nothing without a PIL`() {
        receive("HANG_UP")

        assertNull(shadowOf(application).nextStartedService)
    }

    private fun receive(action: String) = receiver.onReceive(application, Intent(action))
}
