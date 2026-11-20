package org.openvoipalliance.voiplib.repository.call.controls

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.AudioDevice
import org.linphone.core.AudioDevice.Capabilities
import org.linphone.core.Core
import org.linphone.core.IceState
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.StreamType
import org.linphone.core.TransportType
import org.openvoipalliance.voiplib.model.AttendedTransferSession
import org.openvoipalliance.voiplib.model.Call
import org.openvoipalliance.voiplib.repository.LinphoneCoreInstanceManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.linphone.core.Call as LinphoneCall

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LinphoneSipActiveCallControlsRepositoryTest {
    private val core = mockk<Core>(relaxed = true)
    private val manager = mockk<LinphoneCoreInstanceManager> { every { safeLinphoneCore } returns core }
    private val repository = LinphoneSipActiveCallControlsRepository(manager)
    private val linphoneCall = mockk<LinphoneCall>(relaxed = true)
    private val call = Call(linphoneCall)

    private val earpiece = audioDevice(AudioDevice.Type.Earpiece, Capabilities.CapabilityPlay)
    private val speaker = audioDevice(AudioDevice.Type.Speaker, Capabilities.CapabilityPlay)
    private val microphone = audioDevice(AudioDevice.Type.Microphone, Capabilities.CapabilityRecord)
    private val bluetoothOut = audioDevice(AudioDevice.Type.Bluetooth, Capabilities.CapabilityPlay)
    private val bluetoothIn = audioDevice(AudioDevice.Type.Bluetooth, Capabilities.CapabilityRecord)
    private val headset = audioDevice(AudioDevice.Type.Headset, Capabilities.CapabilityAll)

    @Before
    fun setUp() {
        every { core.callsNb } returns 1
        every { core.currentCall } returns linphoneCall
        every { core.audioDevices } returns arrayOf(earpiece, speaker, microphone, bluetoothOut, bluetoothIn, headset)
    }

    @Test
    fun `the microphone is muted by disabling it on the core`() {
        repository.setMicrophone(false)
        verify { core.isMicEnabled = false }

        every { core.isMicEnabled } returns false
        assertTrue(repository.isMicrophoneMuted())

        every { core.isMicEnabled } returns true
        assertFalse(repository.isMicrophoneMuted())
    }

    @Test
    fun `the microphone is not muted without a core`() {
        every { manager.safeLinphoneCore } returns null

        repository.setMicrophone(true)
        assertFalse(repository.isMicrophoneMuted())
    }

    @Test
    fun `hold pauses and resumes the call`() {
        repository.setHold(call, true)
        verify(exactly = 1) { linphoneCall.pause() }

        repository.setHold(call, false)
        verify(exactly = 1) { linphoneCall.resume() }

        repository.pauseCall(call)
        repository.resumeCall(call)
        verify(exactly = 2) {
            linphoneCall.pause()
            linphoneCall.resume()
        }
    }

    @Test
    fun `switching calls pauses one and resumes the other`() {
        val other = mockk<LinphoneCall>(relaxed = true)

        repository.switchCall(call, Call(other))

        verify {
            linphoneCall.pause()
            other.resume()
        }
    }

    @Test
    fun `transfers go through linphone`() {
        val target = mockk<LinphoneCall>(relaxed = true)

        repository.transferUnattended(call, "+31507654321")
        repository.finishAttendedTransfer(AttendedTransferSession(call, Call(target)))

        verify {
            linphoneCall.transfer("+31507654321")
            linphoneCall.transferToAnother(target)
        }
    }

    @Test
    fun `a single DTMF digit is sent as a character, more as a string`() {
        repository.sendDtmf(call, "5")
        repository.sendDtmf(call, "123#")

        verify {
            linphoneCall.sendDtmf('5')
            linphoneCall.sendDtmfs("123#")
        }
    }

    @Test
    fun `audio is not routed without calls`() {
        every { core.callsNb } returns 0

        repository.routeAudioTo(listOf(AudioDevice.Type.Speaker))

        verify(exactly = 0) { linphoneCall.outputAudioDevice = any() }
    }

    @Test
    fun `routing to the speaker records from the microphone`() {
        repository.routeAudioTo(listOf(AudioDevice.Type.Speaker))

        verify {
            linphoneCall.outputAudioDevice = speaker
            linphoneCall.inputAudioDevice = microphone
        }
    }

    @Test
    fun `routing to bluetooth or a headset records from that device`() {
        repository.routeAudioTo(listOf(AudioDevice.Type.Bluetooth))
        verify {
            linphoneCall.outputAudioDevice = bluetoothOut
            linphoneCall.inputAudioDevice = bluetoothIn
        }

        repository.routeAudioTo(listOf(AudioDevice.Type.Headphones, AudioDevice.Type.Headset))
        verify {
            linphoneCall.outputAudioDevice = headset
            linphoneCall.inputAudioDevice = headset
        }
    }

    @Test
    fun `nothing is changed for a route without a matching device`() {
        every { core.audioDevices } returns arrayOf(earpiece)

        repository.routeAudioTo(listOf(AudioDevice.Type.Bluetooth))

        verify(exactly = 0) {
            linphoneCall.outputAudioDevice = any()
            linphoneCall.inputAudioDevice = any()
        }
    }

    @Test
    fun `audio is routed for the given call, else the current call, else the first call`() {
        val given = mockk<LinphoneCall>(relaxed = true)
        repository.routeAudioTo(listOf(AudioDevice.Type.Earpiece), given)
        verify { given.outputAudioDevice = earpiece }

        repository.routeAudioTo(listOf(AudioDevice.Type.Earpiece))
        verify { linphoneCall.outputAudioDevice = earpiece }

        val first = mockk<LinphoneCall>(relaxed = true)
        every { core.currentCall } returns null
        every { core.calls } returns arrayOf(first)
        repository.routeAudioTo(listOf(AudioDevice.Type.Earpiece))
        verify { first.outputAudioDevice = earpiece }
    }

    @Test
    fun `call info describes the call as JSON`() {
        every { linphoneCall.currentParams.usedAudioPayloadType?.mimeType } returns "opus"
        every { linphoneCall.getStats(StreamType.Audio)?.iceState } returns IceState.HostConnection
        every { linphoneCall.getStats(StreamType.Audio)?.roundTripDelay } returns 0.25f
        every { linphoneCall.core.mtu } returns 1300
        every { linphoneCall.core.adaptiveRateAlgorithm } returns "advanced"
        every { linphoneCall.toAddress.transport } returns TransportType.Tls
        every { linphoneCall.toAddress.domain } returns "sip.example.com"
        every { linphoneCall.remoteParams?.mediaEncryption } returns MediaEncryption.SRTP
        every { linphoneCall.remoteParams?.getCustomHeader("P-Asserted-Identity") } returns "\"Alice\" <sip:201@sip.example.com>"
        every { linphoneCall.params.mediaEncryption } returns MediaEncryption.SRTP
        every { linphoneCall.callLog.callId } returns "call-id"
        every { linphoneCall.callLog.dir } returns LinphoneCall.Dir.Outgoing
        every { linphoneCall.callLog.status } returns LinphoneCall.Status.Success
        every { linphoneCall.reason } returns Reason.None
        every { linphoneCall.duration } returns 75
        every { linphoneCall.errorInfo.protocolCode } returns 200
        every { linphoneCall.errorInfo.reason } returns Reason.None

        @Suppress("UNCHECKED_CAST")
        val info = Gson().fromJson(repository.provideCallInfo(call), Map::class.java) as Map<String, Map<String, Any?>>

        assertEquals(
            setOf("audio", "advanced-settings", "to-address", "remote-params", "params", "call", "network", "error"),
            info.keys,
        )
        assertEquals("opus", info["audio"]!!["codecMime"])
        assertEquals("HostConnection", info["audio"]!!["iceState"])
        assertEquals(0.25, info["audio"]!!["roundTripDelay"])
        assertEquals(1300.0, info["advanced-settings"]!!["mtu"])
        assertEquals("advanced", info["advanced-settings"]!!["adaptiveRateAlgorithm"])
        assertEquals(mapOf("transport" to "Tls", "domain" to "sip.example.com"), info["to-address"])
        assertEquals("SRTP", info["remote-params"]!!["encryption"])
        assertEquals("\"Alice\" <sip:201@sip.example.com>", info["remote-params"]!!["pAssertedIdentity"])
        assertEquals("SRTP", info["params"]!!["encryption"])
        assertEquals("call-id", info["call"]!!["callId"])
        assertEquals("Outgoing", info["call"]!!["direction"])
        assertEquals("None", info["call"]!!["reason"])
        assertEquals(75.0, info["call"]!!["duration"])
        assertEquals(200.0, info["error"]!!["protocolCode"])
    }

    private fun audioDevice(type: AudioDevice.Type, capability: Capabilities) = mockk<AudioDevice>(relaxed = true) {
        every { this@mockk.type } returns type
        every { hasCapability(any()) } answers {
            capability == Capabilities.CapabilityAll || firstArg<Capabilities>() == capability
        }
    }
}
