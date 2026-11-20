package org.openvoipalliance.flutterphonelib

import android.app.Activity
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.PIL
import org.openvoipalliance.androidphoneintegration.allowRealPilModuleToLoad
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.androidphoneintegration.configuration.Preferences
import org.openvoipalliance.androidphoneintegration.events.Event
import org.openvoipalliance.androidphoneintegration.logging.LogLevel
import org.openvoipalliance.androidphoneintegration.mockLinphoneFactory
import org.openvoipalliance.androidphoneintegration.push.UnavailableReason
import org.openvoipalliance.androidphoneintegration.restoreLinphoneFactory
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class MainActivity : Activity()

class IncomingCallActivity : Activity()

/** The native start that apps call from their `Application`, using what Dart persisted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartPhoneLibTest {
    private val application = RuntimeEnvironment.getApplication()
    private val auth = Auth("user", "password", "sip.example.com", 5061, true)
    private val preferences = Preferences(useApplicationProvidedRingtone = true, enableAdvancedLogging = true)
    private val logs = mutableListOf<Pair<String, PhoneLibLogLevel>>()
    private val endedCalls = mutableListOf<NativeCall>()
    private val nativeMiddleware = mockk<NativeMiddleware>(relaxed = true)

    @Before
    fun setUp() {
        resetPhoneLib()
        allowRealPilModuleToLoad()
        mockLinphoneFactory()
    }

    @After
    fun tearDown() {
        resetPhoneLib()
        clearPilInstance()
        restoreLinphoneFactory()
        application.sharedPreferences.edit().clear().commit()
    }

    @Test
    fun `nothing starts until Dart has persisted its arguments`() {
        start()

        assertFalse(PhoneLib.isPILInitialized)
        assertSame(application, PhoneLib.app)
        assertEquals(MainActivity::class.java, PhoneLib.activityClass)
        assertEquals(IncomingCallActivity::class.java, PhoneLib.incomingCallActivityClass)
        assertSame(nativeMiddleware, PhoneLib.nativeMiddleware)
        assertNotNull(PhoneLib.onLogReceived)
        assertNotNull(PhoneLib.onCallEnded)
    }

    @Test
    fun `values set before starting are kept`() {
        val onCallEnded: OnCallEndedCallback = {}
        PhoneLib.activityClass = IncomingCallActivity::class.java
        PhoneLib.onCallEnded = onCallEnded

        start()

        assertEquals(IncomingCallActivity::class.java, PhoneLib.activityClass)
        assertSame(onCallEnded, PhoneLib.onCallEnded)
    }

    @Test
    fun `the PIL starts with the persisted arguments`() {
        persist()

        start()

        assertTrue(PhoneLib.isPILInitialized)
        val pil = PhoneLib.pil
        assertSame(pil, PIL.instance)
        assertEquals(auth, pil.auth)
        assertEquals(preferences, pil.preferences)
        assertEquals("Test Agent", pil.app.userAgent)
        assertEquals(MainActivity::class.java, pil.app.activities.call)
        assertEquals(IncomingCallActivity::class.java, pil.app.activities.incomingCall)
    }

    @Test
    fun `the incoming call screen defaults to the main activity`() {
        persist()

        application.startPhoneLib(MainActivity::class.java)

        assertEquals(MainActivity::class.java, PhoneLib.pil.app.activities.incomingCall)
        assertNull(PhoneLib.pil.app.middleware)
    }

    @Test
    fun `a missed call notification opens the main activity marked as pressed`() {
        persist()

        start()

        val intent = shadowOf(PhoneLib.pil.app.onMissedCallNotificationPressed).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(PhoneLib.PRESSED_MISSED_CALL_NOTIFICATION_EXTRA, false))
    }

    @Test
    fun `PIL logs reach the app at the matching level`() {
        persist()
        start()
        logs.clear()
        val logger = PhoneLib.pil.app.logger!!

        LogLevel.values().forEach { logger.onLogReceived(it.name, it) }

        assertEquals(
            listOf(
                "DEBUG" to PhoneLibLogLevel.DEBUG,
                "INFO" to PhoneLibLogLevel.INFO,
                "WARNING" to PhoneLibLogLevel.WARNING,
                "ERROR" to PhoneLibLogLevel.ERROR,
            ),
            logs,
        )
    }

    @Test
    fun `the PIL's middleware is the native middleware`() {
        persist()
        start()
        val message = RemoteMessage.Builder("test@fcm.googleapis.com").build()

        PhoneLib.pil.app.middleware!!.respond(message, false, UnavailableReason.IN_CALL)

        verify { nativeMiddleware.respond(message, false, NativeMiddlewareUnavailableReason.IN_CALL) }
    }

    @Test
    fun `ended calls are reported to the app`() {
        persist()
        start()

        PhoneLib.callListener!!.onEvent(Event.CallSessionEvent.CallEnded(Fixtures.sessionState()))
        PhoneLib.callListener!!.onEvent(Event.CallSessionEvent.CallConnected(Fixtures.sessionState()))

        assertEquals(listOf(Fixtures.activeCall().toNativeCall()), endedCalls)
    }

    @Test
    fun `starting again changes nothing`() {
        persist()
        start()
        val pil = PhoneLib.pil

        start()

        assertSame(pil, PhoneLib.pil)
    }

    @Test
    fun `startCall calls through the PIL once it is started`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        activity.startCall("+31501234567")

        val pil = mockk<PIL>(relaxed = true)
        PhoneLib.pil = pil
        activity.startCall("+31501234567")

        verify(exactly = 1) { pil.call("+31501234567") }
    }

    private fun start() = application.startPhoneLib(
        MainActivity::class.java,
        IncomingCallActivity::class.java,
        nativeMiddleware,
        onCallEnded = { endedCalls += it },
        onLogReceived = { message, level -> logs += message to level },
    )

    private fun persist() = application.sharedPreferences.edit()
        .putString(PhoneLib.Keys.AUTH, Gson().toJson(auth))
        .putString(PhoneLib.Keys.PREFERENCES, Gson().toJson(preferences))
        .putString(PhoneLib.Keys.USER_AGENT, "Test Agent")
        .commit()

    /** The other bridge tests leave a mock PIL behind, and `pil` is a lateinit. */
    private fun resetPhoneLib() {
        PhoneLib::class.java.getDeclaredField("pil").apply { isAccessible = true }.set(null, null)
        PhoneLib.app = null
        PhoneLib.activityClass = null
        PhoneLib.incomingCallActivityClass = null
        PhoneLib.onLogReceived = null
        PhoneLib.nativeMiddleware = null
        PhoneLib.onCallEnded = null
        PhoneLib.callListener = null
    }
}
