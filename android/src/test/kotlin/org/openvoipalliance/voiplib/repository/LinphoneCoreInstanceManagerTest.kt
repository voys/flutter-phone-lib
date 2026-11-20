package org.openvoipalliance.voiplib.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.AudioDevice
import org.linphone.core.Core
import org.linphone.core.GlobalState
import org.linphone.core.LoggingService
import org.linphone.core.MediaEncryption
import org.linphone.core.NatPolicy
import org.linphone.core.PayloadType
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.mockLinphoneFactory
import org.openvoipalliance.androidphoneintegration.restoreLinphoneFactory
import org.openvoipalliance.voiplib.config.Config
import org.openvoipalliance.voiplib.model.Call
import org.openvoipalliance.voiplib.model.Codec
import org.openvoipalliance.voiplib.repository.initialize.CallListener
import org.openvoipalliance.voiplib.repository.initialize.LogLevel
import org.openvoipalliance.voiplib.repository.initialize.LogListener
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.linphone.core.Call as LinphoneCall
import org.linphone.core.LogLevel as LinphoneLogLevel

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [33])
class LinphoneCoreInstanceManagerTest {
    private val factory = mockLinphoneFactory()
    private val core = mockk<Core>(relaxed = true)
    private val natPolicy = mockk<NatPolicy>(relaxed = true)
    private val callListener = mockk<CallListener>(relaxed = true)
    private val logs = CopyOnWriteArrayList<Pair<LogLevel, String>>()
    private val logListener = object : LogListener {
        override fun onLogMessageWritten(lev: LogLevel, message: String) {
            logs += lev to message
        }
    }
    private var readyCount = 0
    private var destroyedCount = 0
    private val config = Config(
        callListener = callListener,
        stun = "stun.example.com",
        logListener = logListener,
        codecs = arrayOf(Codec.OPUS, Codec.PCMA),
        userAgent = "Test Agent",
        onReady = { readyCount++ },
        onDestroy = { destroyedCount++ },
    )
    private val manager = LinphoneCoreInstanceManager(application)

    @Before
    fun setUp() {
        every { factory.createCore("", "", any()) } returns core
        every { core.natPolicy } returns natPolicy
        every { core.audioPayloadTypes } returns arrayOf(payloadType("opus"))
        every { core.videoPayloadTypes } returns emptyArray()
    }

    @After
    fun tearDown() {
        restoreLinphoneFactory()
        LinphoneCoreInstanceManager.globalState = GlobalState.Off
    }

    @Test
    fun `initializing configures the core before starting it`() {
        manager.initializeLinphone(config)

        verifyOrder {
            core.addListener(manager)
            core.mediaEncryption = MediaEncryption.SRTP
            core.start()
            core.useInfoForDtmf = true
        }
        verify {
            core.isMediaEncryptionMandatory = true
            core.setUserAgent("Test Agent", null)
            core.maxCalls = 2
            core.ring = null
            core.isNativeRingingEnabled = false
            core.isVideoCaptureEnabled = false
            core.isVideoDisplayEnabled = false
            core.isPushNotificationEnabled = false
            core.isDnsSearchEnabled = false
            core.isAutoIterateEnabled = true
            core.isIpv6Enabled = false
            core.stunServer = "stun.example.com"
            core.mtu = 1300
            core.guessHostname = true
            core.isKeepAliveEnabled = false
            core.isAdaptiveRateControlEnabled = true
            natPolicy.isStunEnabled = true
        }
        assertTrue(manager.state.initialized)
        assertSame(core, manager.safeLinphoneCore)
    }

    @Test
    fun `the ringback is published to a file for linphone`() {
        manager.initializeLinphone(config)

        val ringback = File(application.filesDir, "apl-resources/ringback.wav")
        assertTrue(ringback.length() > 0)
        verify { core.ringback = ringback.absolutePath }
    }

    @Test
    fun `stun is disabled without a stun server`() {
        manager.initializeLinphone(config.copy(stun = null))

        verify { natPolicy.isStunEnabled = false }
    }

    @Test
    fun `software echo cancellation is only used without a built-in echo canceller`() {
        every { core.hasBuiltinEchoCanceller() } returns true
        manager.initializeLinphone(config)
        verify { core.isEchoCancellationEnabled = false }

        val other = LinphoneCoreInstanceManager(application)
        every { core.hasBuiltinEchoCanceller() } returns false
        other.initializeLinphone(config)
        verify { core.isEchoCancellationEnabled = true }
    }

    @Test
    fun `only the configured audio codecs are enabled and video is disabled`() {
        val opus = payloadType("opus")
        val pcma = payloadType("PCMA")
        val pcmu = payloadType("PCMU")
        val video = payloadType("VP8")
        every { core.audioPayloadTypes } returns arrayOf(opus, pcma, pcmu)
        every { core.videoPayloadTypes } returns arrayOf(video)

        manager.initializeLinphone(config)

        verify {
            opus.enable(true)
            pcma.enable(true)
            pcmu.enable(false)
            video.enable(false)
        }
    }

    // Codec.valueOf throws on any mime type it doesn't list, after the core has started.
    @Test
    fun `an audio codec missing from Codec leaves linphone uninitialized`() {
        every { core.audioPayloadTypes } returns arrayOf(payloadType("opus"), payloadType("telephone-event"))

        manager.initializeLinphone(config)

        verify { core.start() }
        assertFalse(manager.state.initialized)
        assertTrue(logs.any { it.first == LogLevel.ERROR && it.second.startsWith("Failed to start Linphone") })
    }

    @Test
    fun `initializing twice keeps the first core`() {
        manager.initializeLinphone(config)
        manager.initializeLinphone(config)

        verify(exactly = 1) { factory.createCore(any(), any(), any()) }
        assertTrue(LogLevel.WARNING to "Not initializing linphone, already initialized." in logs)
    }

    @Test
    fun `a core that fails to start leaves linphone uninitialized`() {
        every { factory.createCore(any(), any(), any()) } throws RuntimeException("boom")

        manager.initializeLinphone(config)

        assertFalse(manager.state.initialized)
        assertNull(manager.safeLinphoneCore)
        assertTrue(LogLevel.ERROR to "Failed to start Linphone: boom" in logs)
    }

    @Test
    fun `the core is only available while initialized and not destroyed`() {
        assertNull(manager.safeLinphoneCore)

        manager.initializeLinphone(config)
        assertSame(core, manager.safeLinphoneCore)

        manager.state.destroyed = true
        assertFalse(manager.state.initialized)
        assertNull(manager.safeLinphoneCore)
    }

    @Test
    fun `call states are forwarded to the call listener`() {
        manager.initializeLinphone(config)
        val linphoneCall = mockk<LinphoneCall>(relaxed = true)
        val call = slot<Call>()

        fun changeTo(state: LinphoneCall.State) = manager.onCallStateChanged(core, linphoneCall, state, "")

        changeTo(LinphoneCall.State.IncomingReceived)
        verify { callListener.incomingCallReceived(capture(call)) }
        assertSame(linphoneCall, call.captured.linphoneCall)

        changeTo(LinphoneCall.State.OutgoingInit)
        verify { callListener.outgoingCallCreated(any()) }

        changeTo(LinphoneCall.State.Connected)
        verify {
            core.activateAudioSession(true)
            callListener.callConnected(any())
        }

        changeTo(LinphoneCall.State.End)
        changeTo(LinphoneCall.State.Error)
        verify(exactly = 2) { callListener.callEnded(any()) }

        changeTo(LinphoneCall.State.Released)
        verify { callListener.callReleased(any()) }

        changeTo(LinphoneCall.State.OutgoingRinging)
        changeTo(LinphoneCall.State.Paused)
        verify(exactly = 2) { callListener.callUpdated(any()) }
    }

    @Test
    fun `streams only count as started straight after connecting`() {
        manager.initializeLinphone(config)
        val linphoneCall = mockk<LinphoneCall>(relaxed = true)

        fun changeTo(state: LinphoneCall.State) = manager.onCallStateChanged(core, linphoneCall, state, "")

        changeTo(LinphoneCall.State.Connected)
        changeTo(LinphoneCall.State.StreamsRunning)
        changeTo(LinphoneCall.State.Paused)
        changeTo(LinphoneCall.State.StreamsRunning)

        verify(exactly = 1) { callListener.streamsStarted() }
        verify(exactly = 1) { callListener.callUpdated(any()) }
    }

    @Test
    fun `invite headers survive re-invites that drop them`() {
        manager.initializeLinphone(config)
        val linphoneCall = mockk<LinphoneCall>(relaxed = true)
        var userData: Any? = null
        every { linphoneCall.userData } answers { userData }
        every { linphoneCall.userData = any() } answers { userData = firstArg() }

        fun reinvite(pAssertedIdentity: String?, remotePartyId: String?) {
            every { linphoneCall.remoteParams?.getCustomHeader("P-Asserted-Identity") } returns pAssertedIdentity
            every { linphoneCall.remoteParams?.getCustomHeader("Remote-Party-ID") } returns remotePartyId
            manager.onCallStateChanged(core, linphoneCall, LinphoneCall.State.UpdatedByRemote, "")
        }

        reinvite("\"Alice\" <sip:201@sip.example.com>", "\"Bob\" <sip:202@sip.example.com>")
        reinvite(null, "")

        val call = Call(linphoneCall)
        assertEquals("\"Alice\" <sip:201@sip.example.com>", call.pAssertedIdentity)
        assertEquals("\"Bob\" <sip:202@sip.example.com>", call.remotePartyId)

        reinvite("\"Carol\" <sip:203@sip.example.com>", null)
        assertEquals("\"Carol\" <sip:203@sip.example.com>", call.pAssertedIdentity)
    }

    @Test
    fun `global state changes report readiness and destruction`() {
        manager.initializeLinphone(config)

        manager.onGlobalStateChanged(core, GlobalState.Startup, "")
        assertEquals(GlobalState.Startup, LinphoneCoreInstanceManager.globalState)
        assertEquals(0 to 0, readyCount to destroyedCount)

        manager.onGlobalStateChanged(core, GlobalState.On, "")
        assertEquals(1 to 0, readyCount to destroyedCount)

        manager.onGlobalStateChanged(core, GlobalState.Off, "")
        assertEquals(1 to 1, readyCount to destroyedCount)
        assertEquals(GlobalState.Off, LinphoneCoreInstanceManager.globalState)
    }

    @Test
    fun `audio device and transfer changes are forwarded to the call listener`() {
        manager.initializeLinphone(config)
        val audioDevice = mockk<AudioDevice>()
        val transferred = mockk<LinphoneCall>(relaxed = true)
        val merged = slot<Call>()

        manager.onAudioDeviceChanged(core, audioDevice)
        manager.onAudioDevicesListUpdated(core)
        manager.onTransferStateChanged(core, transferred, LinphoneCall.State.Connected)

        verify {
            callListener.currentAudioDeviceHasChanged(audioDevice)
            callListener.availableAudioDevicesUpdated()
            callListener.attendedTransferMerged(capture(merged))
        }
        assertSame(transferred, merged.captured.linphoneCall)
    }

    @Test
    fun `linphone logs are forwarded at the matching level`() {
        manager.initializeLinphone(config)
        logs.clear()
        val service = mockk<LoggingService>()
        val levels = mapOf(
            LinphoneLogLevel.Debug to LogLevel.DEBUG,
            LinphoneLogLevel.Trace to LogLevel.TRACE,
            LinphoneLogLevel.Message to LogLevel.MESSAGE,
            LinphoneLogLevel.Warning to LogLevel.WARNING,
            LinphoneLogLevel.Error to LogLevel.ERROR,
            LinphoneLogLevel.Fatal to LogLevel.FATAL,
        )

        for ((linphoneLevel, level) in levels) {
            manager.onLogMessageWritten(service, "domain", linphoneLevel, level.name)
        }

        // Forwarded from a background coroutine.
        val deadline = System.currentTimeMillis() + 5000
        while (logs.size < levels.size && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(levels.values.map { it to it.name }.toSet(), logs.toSet())
    }

    @Test
    fun `advanced logging switches linphone to debug logs`() {
        manager.setLoggingLevel(enableAdvancedLogging = true)
        verify { factory.loggingService.setLogLevel(LinphoneLogLevel.Debug) }

        manager.setLoggingLevel(enableAdvancedLogging = false)
        verify { factory.loggingService.setLogLevel(LinphoneLogLevel.Warning) }
    }

    // `logListener.let` rather than `?.let`, so this happens with or without one.
    @Test
    fun `linphone logs are listened to even without a log listener`() {
        manager.initializeLinphone(config.copy(logListener = null))

        verify { factory.loggingService.addListener(manager) }
    }

    @Test
    fun `preserved invite data ignores blank values`() {
        val data = PreservedInviteData()
        assertEquals("", data.remotePartyId)
        assertEquals("", data.pAssertedIdentity)

        data.remotePartyId = "first"
        data.pAssertedIdentity = "first"
        data.remotePartyId = " "
        data.pAssertedIdentity = null

        assertEquals("first", data.remotePartyId)
        assertEquals("first", data.pAssertedIdentity)
    }

    private fun payloadType(mimeType: String) = mockk<PayloadType>(relaxed = true) {
        every { this@mockk.mimeType } returns mimeType
    }
}
