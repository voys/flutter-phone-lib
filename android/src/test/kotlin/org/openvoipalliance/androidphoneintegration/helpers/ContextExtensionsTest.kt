package org.openvoipalliance.androidphoneintegration.helpers

import android.content.ComponentName
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.ApplicationStateListener
import org.openvoipalliance.androidphoneintegration.TestCallActivity
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.applicationSetup
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.configuration.ApplicationSetup.AutomaticallyLaunchCallActivity
import org.openvoipalliance.androidphoneintegration.mockPil
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContextExtensionsTest {
    @After
    fun tearDown() {
        clearPilInstance()
        ApplicationStateListener.isInForeground = false
    }

    @Test
    fun `the call activity is always launched by default`() {
        mockPil(applicationSetup(AutomaticallyLaunchCallActivity.ALWAYS))
        ApplicationStateListener.isInForeground = true

        application.startCallActivity()

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(ComponentName(application, TestCallActivity::class.java), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `the call activity is never launched when the app opts out`() {
        mockPil(applicationSetup(AutomaticallyLaunchCallActivity.NEVER))

        application.startCallActivity()

        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `the call activity is only launched from the background when asked`() {
        mockPil(applicationSetup(AutomaticallyLaunchCallActivity.ONLY_FROM_BACKGROUND))

        ApplicationStateListener.isInForeground = true
        application.startCallActivity()
        assertNull(shadowOf(application).nextStartedActivity)

        ApplicationStateListener.isInForeground = false
        application.startCallActivity()
        assertEquals(
            ComponentName(application, TestCallActivity::class.java),
            shadowOf(application).nextStartedActivity.component,
        )
    }

    @Test
    fun `the application is in the foreground when the listener says so`() {
        assertFalse(application.isInForeground)

        ApplicationStateListener.isInForeground = true
        assertTrue(application.isInForeground)
    }
}
