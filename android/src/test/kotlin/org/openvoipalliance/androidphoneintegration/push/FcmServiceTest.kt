package org.openvoipalliance.androidphoneintegration.push

import android.os.Looper
import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.applicationSetup
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.androidphoneintegration.helpers.VoIPLibHelper
import org.openvoipalliance.androidphoneintegration.installKoin
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.telecom.AndroidCallFramework
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FcmServiceTest {
    private val middleware = mockk<Middleware>(relaxed = true)
    private val pil = mockPil(applicationSetup().copy(middleware = middleware))
    private val androidCallFramework = mockk<AndroidCallFramework>()
    private val helper = mockk<VoIPLibHelper>(relaxed = true)
    private val service = Robolectric.buildService(FcmService::class.java).get()
    private val message = RemoteMessage.Builder("test@fcm.googleapis.com").addData("unique_key", "call-id").build()
    private val registered = slot<(Boolean) -> Unit>()

    @Before
    fun setUp() {
        installKoin {
            single { pil }
            single { androidCallFramework }
        }
        every { pil.auth } returns Auth("user", "password", "sip.example.com", 5061, true)
        every { pil.calls.isInCall } returns false
        every { pil.phoneLibHelper } returns helper
        every { pil.voipLib.isNetworkReachable } returns true
        every { androidCallFramework.canHandleIncomingCall } returns true
        every { middleware.inspect(message) } returns true
        every { helper.register(capture(registered)) } returns Unit
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `a call push registers and reports availability`() {
        service.onMessageReceived(message)

        verify { helper.processPushNotification("call-id") }

        registered.captured(true)
        verify { middleware.respond(message, true) }
    }

    @Test
    fun `a call push that can't register reports why`() {
        service.onMessageReceived(message)

        registered.captured(false)
        verify { middleware.respond(message, false, UnavailableReason.UNABLE_TO_REGISTER) }
    }

    @Test
    fun `a push without a call id still registers`() {
        val withoutCallId = RemoteMessage.Builder("test@fcm.googleapis.com").build()
        every { middleware.inspect(withoutCallId) } returns true

        service.onMessageReceived(withoutCallId)

        verify(exactly = 0) { helper.processPushNotification(any()) }
        verify { helper.register(any()) }
    }

    @Test
    fun `a call push during a call is declined`() {
        every { pil.calls.isInCall } returns true

        service.onMessageReceived(message)

        verify { middleware.respond(message, false, UnavailableReason.IN_CALL) }
        verify(exactly = 0) { helper.register(any()) }
    }

    @Test
    fun `a call push telecom can't take is declined`() {
        every { androidCallFramework.canHandleIncomingCall } returns false

        service.onMessageReceived(message)

        verify { middleware.respond(message, false, UnavailableReason.REJECTED_BY_ANDROID_TELECOM_FRAMEWORK) }
        verify(exactly = 0) { helper.register(any()) }
    }

    @Test
    fun `pushes the middleware doesn't recognise as calls are ignored`() {
        every { middleware.inspect(message) } returns false

        service.onMessageReceived(message)

        verify(exactly = 0) {
            middleware.respond(any(), any(), any())
            helper.register(any())
        }
    }

    @Test
    fun `pushes are ignored without valid auth, a middleware or a PIL`() {
        every { pil.auth } returns null
        service.onMessageReceived(message)

        every { pil.auth } returns Auth("user", "password", "sip.example.com", 5061, true)
        every { pil.app } returns applicationSetup()
        service.onMessageReceived(message)

        clearPilInstance()
        service.onMessageReceived(message)

        verify(exactly = 0) {
            middleware.inspect(any())
            helper.register(any())
        }
    }

    // The wait loop runs `while (isNetworkReachable)`, so without a network it doesn't wait at all.
    @Test
    fun `without a network the push registers as soon as the main thread is free`() {
        every { pil.voipLib.isNetworkReachable } returns false

        service.onMessageReceived(message)
        verify(exactly = 0) { helper.register(any()) }

        shadowOf(Looper.getMainLooper()).idle()
        verify { helper.register(any()) }
        verify(exactly = 0) { middleware.respond(message, false, UnavailableReason.NO_CONNECTIVITY) }
    }

    @Test
    fun `new tokens are passed to the middleware`() {
        service.onNewToken("token")

        verify { middleware.tokenReceived("token") }
    }

    @Test
    fun `new tokens are dropped without a PIL`() {
        clearPilInstance()

        service.onNewToken("token")

        verify(exactly = 0) { middleware.tokenReceived(any()) }
    }
}
