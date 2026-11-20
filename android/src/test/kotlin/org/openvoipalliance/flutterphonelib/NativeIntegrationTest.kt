package org.openvoipalliance.flutterphonelib

import com.google.firebase.messaging.RemoteMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.push.UnavailableReason
import org.openvoipalliance.flutterphonelib.push.toMap
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The native-only API that apps use from their `Application` and `MainActivity`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NativeIntegrationTest {
    @After
    fun tearDown() {
        PhoneLib.channel = null
    }

    @Test
    fun `missed call notification presses are forwarded to Dart`() {
        val messenger = RecordingMessenger()
        PhoneLib.channel = messenger.channel()

        PhoneLib.notifyMissedCallNotificationPressed()

        val call = messenger.sent.single()
        assertEquals("onMissedCallNotificationPressed", call.method)
        assertNull(call.arguments)
    }

    @Test
    fun `missed call notification presses are dropped without a channel`() {
        assertNull(PhoneLib.notifyMissedCallNotificationPressed())
    }

    @Test
    fun `toNativeCall summarises the call for onCallEnded`() {
        assertEquals(
            NativeCall(mos = "4.5", reason = "", duration = "75", direction = "outbound"),
            Fixtures.activeCall().toNativeCall(),
        )
        assertEquals(
            NativeCall(mos = "0.0", reason = "Busy Here", duration = "3725", direction = "inbound"),
            Fixtures.inactiveCall().toNativeCall(),
        )
    }

    @Test
    fun `toNativeCall is empty without a call`() {
        assertEquals(NativeCall("", "", "", ""), null.toNativeCall())
    }

    @Test
    fun `unavailable reasons map to their native counterparts`() {
        assertEquals(NativeMiddlewareUnavailableReason.IN_CALL, UnavailableReason.IN_CALL.toNative())
        assertEquals(
            NativeMiddlewareUnavailableReason.REJECTED_BY_ANDROID_TELECOM_FRAMEWORK,
            UnavailableReason.REJECTED_BY_ANDROID_TELECOM_FRAMEWORK.toNative(),
        )
        assertEquals(
            NativeMiddlewareUnavailableReason.UNABLE_TO_REGISTER,
            UnavailableReason.UNABLE_TO_REGISTER.toNative(),
        )
        assertNull(UnavailableReason.NO_CONNECTIVITY.toNative())
        assertNull(null.toNative())
    }

    @Test
    fun `the native middleware receives everything the PIL middleware does`() {
        val native = mockk<NativeMiddleware>(relaxed = true)
        val message = mockk<RemoteMessage>()
        every { native.inspect(message) } returns false

        val middleware = native.toMiddleware()
        middleware.respond(message, available = false, reason = UnavailableReason.IN_CALL)
        middleware.tokenReceived("token")
        val inspected = middleware.inspect(message)

        assertFalse(inspected)
        verify {
            native.respond(message, false, NativeMiddlewareUnavailableReason.IN_CALL)
            native.tokenReceived("token")
            native.inspect(message)
        }
    }

    @Test
    fun `remote messages serialize their data`() {
        val message = mockk<RemoteMessage> {
            every { data } returns mapOf("unique_key" to "abc")
        }

        assertEquals(mapOf("data" to mapOf("unique_key" to "abc")), overTheWire(message.toMap()))
    }
}
