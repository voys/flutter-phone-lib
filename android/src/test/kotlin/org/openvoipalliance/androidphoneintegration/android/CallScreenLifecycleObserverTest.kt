package org.openvoipalliance.androidphoneintegration.android

import android.app.Activity
import android.app.KeyguardManager
import android.media.AudioManager
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event
import org.openvoipalliance.androidphoneintegration.events.PILEventListener
import org.openvoipalliance.androidphoneintegration.mockPil
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class ListeningCallActivity : Activity(), PILEventListener {
    override fun onEvent(event: Event) {}
}

class PlainCallActivity : Activity()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallScreenLifecycleObserverTest {
    private val pil = mockPil()

    // Robolectric's keyguard calls back straight away when unlocked, and the observer passes none.
    @Before
    fun setUp() {
        shadowOf(application.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `the call screen shows over the lock screen and controls call volume`() {
        val activity = Robolectric.buildActivity(PlainCallActivity::class.java).create().get()

        CallScreenLifecycleObserver(activity).begin()

        assertTrue(shadowOf(activity).showWhenLocked)
        assertTrue(shadowOf(activity).turnScreenOn)
        assertEquals(AudioManager.STREAM_VOICE_CALL, activity.volumeControlStream)
    }

    @Test
    fun `a call screen that listens to events does so while it exists`() {
        val activity = Robolectric.buildActivity(ListeningCallActivity::class.java).create().get()
        val observer = CallScreenLifecycleObserver(activity)

        observer.begin()
        verify { pil.events.listen(activity) }

        observer.end()
        verify { pil.events.stopListening(activity) }
    }

    @Test
    fun `a call screen that doesn't listen isn't registered`() {
        val activity = Robolectric.buildActivity(PlainCallActivity::class.java).create().get()
        val observer = CallScreenLifecycleObserver(activity)

        observer.begin()
        observer.end()

        verify(exactly = 0) {
            pil.events.listen(any())
            pil.events.stopListening(any())
        }
    }
}
