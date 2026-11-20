package org.openvoipalliance.androidphoneintegration.push

import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TokenFetcherTest {
    private val middleware = mockk<Middleware>(relaxed = true)
    private val messaging = mockk<FirebaseMessaging>()
    private val tokenTask = mockk<Task<String>>(relaxed = true)
    private val fetcher = TokenFetcher(middleware)

    @Before
    fun setUp() {
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseMessaging.getInstance() } returns messaging
        every { messaging.token } returns tokenTask
    }

    @After
    fun tearDown() = unmockkStatic(FirebaseMessaging::class)

    @Test
    fun `requesting asks firebase for the token`() {
        fetcher.request()

        verify { tokenTask.addOnCompleteListener(fetcher) }
    }

    @Test
    fun `nothing is requested without a middleware`() {
        TokenFetcher(null).request()

        verify(exactly = 0) { FirebaseMessaging.getInstance() }
    }

    @Test
    fun `a fetched token is passed to the middleware`() {
        fetcher.onComplete(task(successful = true, result = "token"))

        verify { middleware.tokenReceived("token") }
    }

    @Test
    fun `a failed fetch is retried once after a minute`() {
        fetcher.onComplete(task(successful = false))
        shadowOf(Looper.getMainLooper()).idleFor(TokenFetcher.RETRY_TIME - 1, TimeUnit.MILLISECONDS)
        verify(exactly = 0) { tokenTask.addOnCompleteListener(fetcher) }

        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        verify(exactly = 1) { tokenTask.addOnCompleteListener(fetcher) }

        fetcher.onComplete(task(successful = false))
        shadowOf(Looper.getMainLooper()).idleFor(TokenFetcher.RETRY_TIME, TimeUnit.MILLISECONDS)
        verify(exactly = 1) { tokenTask.addOnCompleteListener(fetcher) }
        verify(exactly = 0) { middleware.tokenReceived(any()) }
    }

    private fun task(successful: Boolean, result: String? = null) = mockk<Task<String>> {
        every { isSuccessful } returns successful
        every { this@mockk.result } returns result
        every { exception } returns null
    }
}
