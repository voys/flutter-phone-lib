package org.openvoipalliance.androidphoneintegration

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.android.PlatformIntegrator
import org.openvoipalliance.androidphoneintegration.audio.AudioManager
import org.openvoipalliance.androidphoneintegration.audio.AudioRoute
import org.openvoipalliance.androidphoneintegration.audio.AudioState
import org.openvoipalliance.androidphoneintegration.audio.LocalDtmfToneGenerator
import org.openvoipalliance.androidphoneintegration.call.CallActions
import org.openvoipalliance.androidphoneintegration.call.Calls
import org.openvoipalliance.androidphoneintegration.call.VoipLibEventTranslator
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.androidphoneintegration.configuration.Preferences
import org.openvoipalliance.androidphoneintegration.debug.VersionInfo
import org.openvoipalliance.androidphoneintegration.events.Event.CallSetupFailedEvent
import org.openvoipalliance.androidphoneintegration.events.Event.CallSetupFailedEvent.OutgoingCallSetupFailed
import org.openvoipalliance.androidphoneintegration.events.EventsManager
import org.openvoipalliance.androidphoneintegration.exception.NoAuthenticationCredentialsException
import org.openvoipalliance.androidphoneintegration.helpers.VoIPLibHelper
import org.openvoipalliance.androidphoneintegration.logging.LogManager
import org.openvoipalliance.androidphoneintegration.notifications.NotificationManager
import org.openvoipalliance.androidphoneintegration.telecom.AndroidCallFramework
import org.openvoipalliance.voiplib.RegistrationCallback
import org.openvoipalliance.voiplib.VoIPLib
import org.openvoipalliance.voiplib.config.Config
import org.openvoipalliance.voiplib.model.Codec
import org.openvoipalliance.voiplib.model.RegistrationState
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [33])
class PILTest {
    private val voipLib = mockk<VoIPLib>(relaxed = true)
    private val helper = mockk<VoIPLibHelper>(relaxed = true)
    private val androidCallFramework = mockk<AndroidCallFramework>(relaxed = true)
    private val platformIntegrator = mockk<PlatformIntegrator>(relaxed = true)
    private val logManager = mockk<LogManager>(relaxed = true)
    private val notifications = mockk<NotificationManager>(relaxed = true)
    private val translator = mockk<VoipLibEventTranslator>(relaxed = true)
    private val telephonyManager = mockk<TelephonyManager>(relaxed = true)
    private val actions = mockk<CallActions>(relaxed = true)
    private val audio = mockk<AudioManager>(relaxed = true)
    private val events = mockk<EventsManager>(relaxed = true)
    private val calls = mockk<Calls>(relaxed = true)
    private val toneGenerator = mockk<LocalDtmfToneGenerator>(relaxed = true)
    private val auth = Auth("user", "password", "sip.example.com", 5061, true)
    private lateinit var pil: PIL

    @Before
    fun setUp() {
        installKoin {
            single { voipLib }
            single { helper }
            single { androidCallFramework }
            single { platformIntegrator }
            single { logManager }
            single { notifications }
            single { translator }
            single { telephonyManager }
            single { actions }
            single { audio }
            single { events }
            single { calls }
            single { toneGenerator }
        }
        every { androidCallFramework.canMakeOutgoingCall } returns true
        shadowOf(application).grantPermissions(Manifest.permission.CALL_PHONE)
        pil = PIL(applicationSetup().copy(userAgent = "Test Agent"))
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `creating the PIL makes it the instance and starts the voip lib with opus only`() {
        val config = slot<Config>()

        assertTrue(PIL.isInitialized)
        assertSame(pil, PIL.instance)
        verify {
            events.listen(platformIntegrator)
            voipLib.initialize(capture(config))
        }
        assertSame(translator, config.captured.callListener)
        assertSame(logManager, config.captured.logListener)
        assertEquals(listOf(Codec.OPUS), config.captured.codecs.toList())
        assertEquals("Test Agent", config.captured.userAgent)
    }

    @Test
    fun `calls are placed through the telecom framework`() {
        pil.call("+31501234567")

        verify { androidCallFramework.placeCall("+31501234567") }
    }

    @Test
    fun `emergency numbers are opened in the native dialer`() {
        every { telephonyManager.isEmergencyNumber("112") } returns true

        pil.call("112")

        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals(Uri.parse("tel:112"), intent.data)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        verify(exactly = 0) { androidCallFramework.placeCall(any()) }
    }

    @Test
    fun `no call is placed while another app is in a call`() {
        every { androidCallFramework.isInCall } returns true

        pil.call("+31501234567")

        assertCallSetupFailed(CallSetupFailedEvent.Reason.IN_CALL)
    }

    @Test
    fun `no call is placed when telecom doesn't permit it`() {
        every { androidCallFramework.canMakeOutgoingCall } returns false

        pil.call("+31501234567")

        assertCallSetupFailed(CallSetupFailedEvent.Reason.REJECTED_BY_ANDROID_TELECOM_FRAMEWORK)
    }

    @Test
    fun `starting registers with the current auth`() {
        pil.auth = auth
        every { helper.register(any()) } answers { firstArg<(Boolean) -> Unit>()(true) }
        val results = mutableListOf<Boolean>()

        pil.start { results += it }

        verify {
            androidCallFramework.prune()
            helper.register(any())
        }
        assertEquals(listOf(true), results)
    }

    @Test
    fun `starting needs the call phone permission`() {
        shadowOf(application).denyPermissions(Manifest.permission.CALL_PHONE)
        pil.auth = auth
        val results = mutableListOf<Boolean>()

        pil.start { results += it }

        assertEquals(listOf(false), results)
        verify(exactly = 0) { helper.register(any()) }
    }

    @Test
    fun `starting needs valid auth`() {
        assertThrows(NoAuthenticationCredentialsException::class.java) { pil.start() }

        pil.auth = auth.copy(password = "")
        assertThrows(NoAuthenticationCredentialsException::class.java) { pil.start() }
    }

    @Test
    fun `stopping forgets the auth and unregisters`() {
        pil.auth = auth

        pil.stop()

        assertNull(pil.auth)
        verify { voipLib.unregister() }
    }

    @Test
    fun `unregistering waits for the voip lib`() {
        val callback = {}

        pil.unregister(callback)

        verify { voipLib.unregisterAndWait(callback) }
    }

    @Test
    fun `the log level is only changed when advanced logging is toggled`() {
        pil.preferences = Preferences(useApplicationProvidedRingtone = true)
        verify(exactly = 0) { voipLib.setAppropriateLogLevel() }

        pil.preferences = Preferences(useApplicationProvidedRingtone = true, enableAdvancedLogging = true)
        verify(exactly = 1) { voipLib.setAppropriateLogLevel() }
    }

    @Test
    fun `new version info is logged`() {
        pil.versionInfo = VersionInfo("pil", "voip")

        verify { logManager.logVersion() }
    }

    @Test
    fun `the PIL is started once it has auth and an initialized voip lib`() {
        every { voipLib.isInitialized } returns true
        assertFalse(pil.isStarted)

        pil.auth = auth
        assertTrue(pil.isStarted)

        every { voipLib.isInitialized } returns false
        assertFalse(pil.isStarted)
    }

    @Test
    fun `state comes from its parts`() {
        val call = pilCall()
        val audioState = AudioState(AudioRoute.PHONE, emptyArray(), null, false, emptyArray())
        every { calls.active } returns call
        every { calls.inactive } returns null
        every { audio.state } returns audioState
        every { voipLib.isRegistered } returns true

        assertEquals(CallSessionState(call, null, audioState), pil.sessionState)
        assertTrue(pil.isRegistered)
        assertSame(actions, pil.actions)
        assertSame(audio, pil.audio)
        assertSame(events, pil.events)
        assertSame(calls, pil.calls)
    }

    @Test
    fun `tones and echo calibration are passed on`() {
        pil.playToneLocally('#')
        pil.performEchoCancellationCalibration()

        verify {
            toneGenerator.play('#')
            voipLib.startEchoCancellerCalibration()
        }
    }

    @Test
    fun `a registration check reports whether registration succeeded`() {
        pil.auth = auth

        every { voipLib.register(any()) } answers {
            firstArg<RegistrationCallback>()(RegistrationState.PROGRESS)
            firstArg<RegistrationCallback>()(RegistrationState.REGISTERED)
            voipLib
        }
        assertTrue(runBlocking { pil.performRegistrationCheck() })

        every { voipLib.register(any()) } answers {
            firstArg<RegistrationCallback>()(RegistrationState.FAILED)
            voipLib
        }
        assertFalse(runBlocking { pil.performRegistrationCheck() })

        every { voipLib.register(any()) } throws IllegalArgumentException()
        assertFalse(runBlocking { pil.performRegistrationCheck() })
    }

    @Test
    fun `a registration check fails straight away with invalid auth`() {
        pil.auth = auth.copy(port = 0)

        assertFalse(runBlocking { pil.performRegistrationCheck() })
        verify(exactly = 0) { voipLib.register(any()) }
    }

    // Only invalid auth is caught up front; without any auth it still tries to register.
    @Test
    fun `a registration check without auth still registers`() {
        every { voipLib.register(any()) } answers {
            firstArg<RegistrationCallback>()(RegistrationState.FAILED)
            voipLib
        }

        assertFalse(runBlocking { pil.performRegistrationCheck() })
        verify { voipLib.register(any()) }
    }

    private fun assertCallSetupFailed(reason: CallSetupFailedEvent.Reason) {
        val event = slot<OutgoingCallSetupFailed>()
        verify { events.broadcast(capture(event)) }
        assertEquals(reason, event.captured.reason)
        verify(exactly = 0) { androidCallFramework.placeCall(any()) }
    }
}
