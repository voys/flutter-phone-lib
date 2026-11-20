package org.openvoipalliance.flutterphonelib

import android.app.Activity
import android.app.Application
import com.google.gson.Gson
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.PIL
import org.openvoipalliance.androidphoneintegration.audio.AudioManager
import org.openvoipalliance.androidphoneintegration.audio.AudioRoute
import org.openvoipalliance.androidphoneintegration.audio.BluetoothAudioRoute
import org.openvoipalliance.androidphoneintegration.call.CallActions
import org.openvoipalliance.androidphoneintegration.call.Calls
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.androidphoneintegration.configuration.Preferences
import org.openvoipalliance.androidphoneintegration.debug.VersionInfo
import org.openvoipalliance.androidphoneintegration.events.EventsManager
import org.openvoipalliance.androidphoneintegration.events.PILEventListener
import org.openvoipalliance.flutterphonelib.events.ProxyEventListener
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Replays `dart_to_native.json`, which the Dart tests prove is what Dart sends, through
 * [PhoneLib.onMethodCall] and checks what reaches the PIL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DartToNativeContractTest {
    private val application: Application = RuntimeEnvironment.getApplication()
    private val actions = mockk<CallActions>(relaxed = true)
    private val audio = mockk<AudioManager>(relaxed = true)
    private val events = mockk<EventsManager>(relaxed = true)
    private val calls = mockk<Calls>(relaxed = true)
    private val pil = mockk<PIL>(relaxed = true)
    private val messenger = RecordingMessenger()
    private val plugin = PhoneLib()

    private val contractAuth = Auth("contract-user", "contract-password", "sip.example.com", 5061, true)

    @Before
    fun setUp() {
        every { pil.actions } returns actions
        every { pil.audio } returns audio
        every { pil.events } returns events
        every { pil.calls } returns calls
        every { pil.sessionState } returns Fixtures.idleSessionState()
        every { pil.versionInfo } returns VersionInfo("pil", "5.5.35")
        every { pil.unregister(any()) } answers { firstArg<() -> Unit>().invoke() }
        every { audio.state } returns Fixtures.idleSessionState().audioState
        every { audio.isMicrophoneMuted } returns false
        every { calls.active } returns null

        var auth: Auth? = null
        var preferences = Preferences.DEFAULT
        every { pil.auth } answers { auth }
        every { pil.auth = any() } answers { auth = firstArg() }
        every { pil.preferences } answers { preferences }
        every { pil.preferences = any() } answers { preferences = firstArg() }

        PhoneLib.pil = pil
        PhoneLib.app = application
        PhoneLib.activityClass = Activity::class.java

        plugin.onAttachedToEngine(mockk<FlutterPlugin.FlutterPluginBinding> {
            every { binaryMessenger } returns messenger
            every { applicationContext } returns application
        })
        plugin.onAttachedToActivity(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        plugin.onDetachedFromEngine(mockk(relaxed = true))
        application.sharedPreferences.edit().clear().commit()
        PhoneLib.app = null
        PhoneLib.activityClass = null
    }

    @Test
    fun `every call in the contract is handled`() {
        val unsupported = setOf("AudioManager.launchAudioRoutePicker")

        for (call in Contract.dartToNative) {
            val method = call["method"] as String
            val outcome = invoke(method, call["arguments"])

            if (method in unsupported) {
                assertEquals(method, Outcome.NotImplemented, outcome)
            } else {
                assertTrue("$method: $outcome", outcome is Outcome.Success)
            }
        }
    }

    @Test
    fun `initializePhoneLib persists its arguments for a native start`() {
        invoke(contractCall("initializePhoneLib"))

        val prefs = application.sharedPreferences
        assertEquals(contractAuth, Gson().fromJson(prefs.getString("auth", null), Auth::class.java))
        assertEquals(
            Preferences(useApplicationProvidedRingtone = true, enableAdvancedLogging = true),
            Gson().fromJson(prefs.getString("preferences", null), Preferences::class.java),
        )
        assertEquals("Contract Test Agent", prefs.getString("userAgent", null))
    }

    @Test
    fun `start configures and starts the PIL`() {
        invoke(contractCall("PhoneLib.start"))

        verify {
            pil.preferences = Preferences(useApplicationProvidedRingtone = true, enableAdvancedLogging = true)
            pil.auth = contractAuth
            pil.start()
        }
        assertEquals(
            contractAuth,
            Gson().fromJson(application.sharedPreferences.getString("auth", null), Auth::class.java),
        )
    }

    @Test
    fun `stop clears the persisted state and stops the PIL`() {
        invoke(contractCall("initializePhoneLib"))
        invoke(contractCall("PhoneLib.stop"))

        assertTrue(application.sharedPreferences.all.isEmpty())
        verify { pil.stop() }
    }

    @Test
    fun `updatePreferences updates and persists the preferences`() {
        invoke(contractCall("PhoneLib.updatePreferences"))

        val expected = Preferences(useApplicationProvidedRingtone = false, enableAdvancedLogging = false)
        verify { pil.preferences = expected }
        assertEquals(
            expected,
            Gson().fromJson(application.sharedPreferences.getString("preferences", null), Preferences::class.java),
        )
    }

    @Test
    fun `unregister replies only once the PIL has unregistered`() {
        var callback: (() -> Unit)? = null
        every { pil.unregister(any()) } answers { callback = firstArg() }

        val result = RecordingResult()
        plugin.onMethodCall(methodCallOverTheWire("PhoneLib.unregister", null), result)
        assertEquals(Outcome.NoReply, result.outcome)

        callback!!.invoke()
        assertEquals(Outcome.Success(null), result.outcome)
    }

    @Test
    fun `PhoneLib methods reach the PIL`() {
        invoke(contractCall("PhoneLib.call"))
        invoke(contractCall("PhoneLib.performEchoCancellationCalibration"))
        invoke(contractCall("PhoneLib.playToneLocally"))

        verify {
            pil.call("+31501234567")
            pil.performEchoCancellationCalibration()
            pil.playToneLocally('5')
        }
        assertEquals(Outcome.Success("5.5.35"), invoke(contractCall("PhoneLib.linphoneVersion")))
    }

    @Test
    fun `sessionState returns the contract shape`() {
        every { pil.sessionState } returns Fixtures.sessionState()

        val outcome = invoke(contractCall("PhoneLib.sessionState")) as Outcome.Success

        assertEquals(normalized(Contract.androidToDart["sessionState"]), normalized(outcome.value))
    }

    @Test
    fun `Calls active returns the active call or null`() {
        assertEquals(Outcome.Success(null), invoke(contractCall("Calls.active")))

        every { calls.active } returns Fixtures.activeCall()
        val outcome = invoke(contractCall("Calls.active")) as Outcome.Success

        assertEquals(
            normalized((Contract.androidToDart["sessionState"] as Map<*, *>)["activeCall"]),
            normalized(outcome.value),
        )
    }

    @Test
    fun `EventsManager listens and stops listening with the same proxy listener`() {
        val listened = mutableListOf<PILEventListener>()
        every { events.listen(capture(listened)) } returns Unit
        every { events.stopListening(capture(listened)) } returns Unit

        invoke(contractCall("EventsManager.listen"))
        invoke(contractCall("EventsManager.stopListening"))

        assertEquals(2, listened.size)
        assertTrue(listened[0] is ProxyEventListener)
        assertTrue(listened[0] === listened[1])
    }

    @Test
    fun `CallActions reach the PIL`() {
        listOf(
            "hold", "unhold", "toggleHold", "sendDtmf", "beginAttendedTransfer",
            "completeAttendedTransfer", "answer", "decline", "end",
        ).forEach { invoke(contractCall("CallActions.$it")) }

        verify {
            actions.hold()
            actions.unhold()
            actions.toggleHold()
            actions.sendDtmf('7', playToneLocally = false)
            actions.beginAttendedTransfer("+31507654321")
            actions.completeAttendedTransfer()
            actions.answer()
            actions.decline()
            actions.end()
        }
    }

    // Dart accepts a string, but only its first character is sent.
    @Test
    fun `sendDtmf sends only the first character`() {
        invoke("CallActions.sendDtmf", mapOf("dtmf" to "123", "playToneLocally" to true))

        verify { actions.sendDtmf('1', playToneLocally = true) }
    }

    @Test
    fun `AudioManager calls reach the PIL`() {
        assertEquals(Outcome.Success(false), invoke(contractCall("AudioManager.isMicrophoneMuted")))
        assertEquals(
            normalized(Contract.androidToDart["idleSessionState"].let { (it as Map<*, *>)["audioState"] }),
            normalized((invoke(contractCall("AudioManager.state")) as Outcome.Success).value),
        )

        Contract.dartToNative
            .filter { it["method"] == "AudioManager.routeAudio" }
            .forEach { invoke("AudioManager.routeAudio", it["arguments"]) }
        listOf("mute", "unmute", "toggleMute").forEach { invoke(contractCall("AudioManager.$it")) }

        verify {
            audio.routeAudio(AudioRoute.SPEAKER)
            audio.routeAudio(BluetoothAudioRoute("Headset", "00:11:22:33:44:55"))
            audio.mute()
            audio.unmute()
            audio.toggleMute()
        }
    }

    @Test
    fun `native exceptions become errors named after the exception`() {
        every { pil.call(any()) } throws IllegalStateException("No connectivity")

        assertEquals(
            Outcome.Error("IllegalStateException", "No connectivity"),
            invoke(contractCall("PhoneLib.call")),
        )
    }

    @Test
    fun `unknown methods are not implemented`() {
        listOf("PhoneLib.unknown", "Calls.unknown", "EventsManager.unknown", "CallActions.unknown",
            "AudioManager.unknown", "Unknown.method", "unknown").forEach {
            assertEquals(it, Outcome.NotImplemented, invoke(it, null))
        }
    }

    // Background isolates (e.g. firebase_messaging) attach an engine without an activity.
    @Test
    fun `the channel is only created once an activity is attached`() {
        plugin.onDetachedFromEngine(mockk(relaxed = true))

        val background = PhoneLib()
        background.onAttachedToEngine(mockk<FlutterPlugin.FlutterPluginBinding> {
            every { binaryMessenger } returns RecordingMessenger()
            every { applicationContext } returns application
        })
        assertNull(PhoneLib.channel)

        background.onAttachedToActivity(mockk(relaxed = true))
        assertTrue(PhoneLib.channel != null)
    }

    private fun contractCall(method: String) =
        Contract.dartToNative.first { it["method"] == method }

    private fun invoke(call: Map<String, Any?>) = invoke(call["method"] as String, call["arguments"])

    private fun invoke(method: String, arguments: Any?): Outcome {
        val result = RecordingResult()
        plugin.onMethodCall(methodCallOverTheWire(method, arguments), result)
        return result.outcome
    }
}
