package org.openvoipalliance.voiplib

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.Core
import org.linphone.core.RegistrationState
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.configuration.Preferences
import org.openvoipalliance.androidphoneintegration.installKoin
import org.openvoipalliance.androidphoneintegration.mockLinphoneFactory
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.restoreLinphoneFactory
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.openvoipalliance.voiplib.config.Config
import org.openvoipalliance.voiplib.repository.LinphoneCoreInstanceManager
import org.openvoipalliance.voiplib.repository.call.controls.LinphoneSipActiveCallControlsRepository
import org.openvoipalliance.voiplib.repository.call.session.LinphoneSipSessionRepository
import org.openvoipalliance.voiplib.repository.registration.LinphoneSipRegisterRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [33])
class VoIPLibTest {
    private val register = mockk<LinphoneSipRegisterRepository>(relaxed = true)
    private val controls = mockk<LinphoneSipActiveCallControlsRepository>(relaxed = true)
    private val session = mockk<LinphoneSipSessionRepository>(relaxed = true)
    private val manager = mockk<LinphoneCoreInstanceManager>(relaxed = true)
    private val core = mockk<Core>(relaxed = true)
    private val pil = mockPil()
    private lateinit var voipLib: VoIPLib

    @Before
    fun setUp() {
        mockLinphoneFactory()
        installKoin {
            single { register }
            single { controls }
            single { session }
            single { manager }
        }
        every { manager.safeLinphoneCore } returns core
        voipLib = VoIPLib()
    }

    @After
    fun tearDown() {
        restoreLinphoneFactory()
        clearPilInstance()
    }

    @Test
    fun `initializing starts linphone at the preferred log level`() {
        val config = Config()
        every { pil.preferences } returns Preferences(useApplicationProvidedRingtone = false, enableAdvancedLogging = true)

        assertSame(voipLib, voipLib.initialize(config))

        verify {
            manager.initializeLinphone(config)
            manager.setLoggingLevel(true)
        }
    }

    @Test
    fun `state comes from the linphone core`() {
        every { manager.state.initialized } returns true
        every { core.defaultAccount?.state } returns RegistrationState.Ok
        every { core.isNetworkReachable } returns true
        every { core.version } returns "5.5.35"

        assertTrue(voipLib.isInitialized)
        assertTrue(voipLib.isRegistered)
        assertTrue(voipLib.isNetworkReachable)
        assertEquals("5.5.35", voipLib.version)

        every { core.defaultAccount?.state } returns RegistrationState.Progress
        assertFalse(voipLib.isRegistered)
    }

    @Test
    fun `without a core it is not registered, reachable or versioned`() {
        every { manager.safeLinphoneCore } returns null

        assertFalse(voipLib.isRegistered)
        assertFalse(voipLib.isNetworkReachable)
        assertEquals("", voipLib.version)
        voipLib.wake()
        voipLib.processPushNotification("call-id")
        voipLib.startEchoCancellerCalibration()
    }

    @Test
    fun `registration goes through the register repository`() {
        val callback: RegistrationCallback = {}
        val onComplete = {}

        assertSame(voipLib, voipLib.register(callback))
        voipLib.unregister()
        voipLib.unregisterAndWait(onComplete)

        verify {
            register.register(callback)
            register.unregister()
            register.unregisterAndWait(onComplete)
        }
    }

    @Test
    fun `calls are placed through the session repository`() {
        val call = voipLibCall()
        every { session.callTo("+31501234567") } returns call

        assertSame(call, voipLib.callTo("+31501234567"))
    }

    @Test
    fun `muting the microphone disables it`() {
        voipLib.microphoneMuted = true
        verify { controls.setMicrophone(false) }

        every { controls.isMicrophoneMuted() } returns true
        assertTrue(voipLib.microphoneMuted)
    }

    @Test
    fun `core actions reach the linphone core`() {
        voipLib.wake()
        voipLib.processPushNotification("call-id")
        voipLib.startEchoCancellerCalibration()

        verify {
            core.ensureRegistered()
            core.processPushNotification("call-id")
            core.startEchoCancellerCalibration()
        }
    }

    @Test
    fun `the log level follows the advanced logging preference`() {
        every { pil.preferences } returns Preferences.DEFAULT

        voipLib.setAppropriateLogLevel()

        verify { manager.setLoggingLevel(false) }
    }
}
