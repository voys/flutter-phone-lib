package org.openvoipalliance.androidphoneintegration

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.call.CallState
import org.openvoipalliance.androidphoneintegration.configuration.ApplicationSetup.AutomaticallyLaunchCallActivity
import org.openvoipalliance.androidphoneintegration.exception.NoAuthenticationCredentialsException
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ApplicationStateListenerTest {
    private val pil = mockPil(applicationSetup(AutomaticallyLaunchCallActivity.ALWAYS))
    private val listener = ApplicationStateListener(pil)

    @After
    fun tearDown() {
        clearPilInstance()
        ApplicationStateListener.isInForeground = false
    }

    @Test
    fun `entering the foreground starts the PIL`() {
        every { pil.calls.active } returns null

        listener.onStart(mockk())

        assertTrue(ApplicationStateListener.isInForeground)
        verify { pil.start() }
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `entering the foreground during a connected call shows the call`() {
        every { pil.calls.active } returns pilCall(state = CallState.CONNECTED)

        listener.onStart(mockk())

        assertNotNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun `a PIL that can't start is logged, not thrown`() {
        every { pil.start() } throws NoAuthenticationCredentialsException()

        listener.onStart(mockk())

        verify { pil.writeLog(match { it.startsWith("Unable to start PIL when entering foreground") }, any()) }
    }

    @Test
    fun `entering the background is remembered`() {
        listener.onStart(mockk())
        listener.onStop(mockk())

        assertFalse(ApplicationStateListener.isInForeground)
    }
}
