package org.openvoipalliance.androidphoneintegration.audio

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Looper
import android.telecom.CallAudioState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.call.CallArrayList
import org.openvoipalliance.androidphoneintegration.call.Calls
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event.CallSessionEvent.AudioStateUpdated
import org.openvoipalliance.androidphoneintegration.events.EventsManager
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.telecom.AndroidCallFramework
import org.openvoipalliance.androidphoneintegration.telecom.Connection
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.openvoipalliance.voiplib.VoIPLib
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import android.media.AudioManager as AndroidAudioManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioManagerTest {
    private val voipLib = mockk<VoIPLib>(relaxed = true)
    private val connection = mockk<Connection>(relaxed = true)
    private val androidCallFramework = mockk<AndroidCallFramework> { every { connection } returns this@AudioManagerTest.connection }
    private val events = mockk<EventsManager>(relaxed = true)
    private val androidAudioManager = mockk<AndroidAudioManager>(relaxed = true)
    private val calls = Calls(mockk(relaxed = true), CallArrayList(Calls.MAX_CALLS))
    private val audio = AudioManager(application, voipLib, androidCallFramework, events, androidAudioManager, calls)
    private val headset = bluetoothDevice("Headset")
    private val carKit = bluetoothDevice("Car kit")
    private val callAudioState = mockk<CallAudioState> {
        every { route } returns CallAudioState.ROUTE_EARPIECE
        every { supportedRouteMask } returns
            (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER or CallAudioState.ROUTE_BLUETOOTH)
        every { activeBluetoothDevice } returns headset
        every { supportedBluetoothDevices } returns listOf(headset, carKit, bluetoothDevice(null))
    }

    @Before
    fun setUp() {
        mockPil()
        every { connection.callAudioState } returns callAudioState
        shadowOf(application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `the state describes the connection's audio`() {
        every { voipLib.microphoneMuted } returns true

        val state = audio.state

        assertEquals(AudioRoute.PHONE, state.currentRoute)
        assertArrayEquals(arrayOf(AudioRoute.BLUETOOTH, AudioRoute.PHONE, AudioRoute.SPEAKER), state.availableRoutes)
        assertEquals("Headset", state.bluetoothDeviceName)
        assertTrue(state.isMicrophoneMuted)
        assertArrayEquals(
            arrayOf(BluetoothAudioRoute("Headset", "Headset"), BluetoothAudioRoute("Car kit", "Car kit")),
            state.bluetoothRoutes,
        )
    }

    @Test
    fun `native routes map to audio routes`() {
        val expected = mapOf(
            CallAudioState.ROUTE_BLUETOOTH to AudioRoute.BLUETOOTH,
            CallAudioState.ROUTE_EARPIECE to AudioRoute.PHONE,
            CallAudioState.ROUTE_WIRED_HEADSET to AudioRoute.PHONE,
            CallAudioState.ROUTE_WIRED_OR_EARPIECE to AudioRoute.PHONE,
            CallAudioState.ROUTE_SPEAKER to AudioRoute.SPEAKER,
        )

        for ((route, audioRoute) in expected) {
            every { callAudioState.route } returns route

            assertEquals(audioRoute, audio.state.currentRoute)
        }
    }

    @Test
    fun `bluetooth details are hidden without the bluetooth permission`() {
        shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        val state = audio.state

        assertNull(state.bluetoothDeviceName)
        assertEquals(0, state.bluetoothRoutes.size)
    }

    @Test
    fun `without a connection the state is the default`() {
        every { androidCallFramework.connection } returns null
        assertDefault(audio.state)

        every { androidCallFramework.connection } returns connection
        every { connection.callAudioState } returns null
        assertDefault(audio.state)
    }

    @Test
    fun `routing goes through the connection and is announced`() {
        val expected = mapOf(
            AudioRoute.SPEAKER to CallAudioState.ROUTE_SPEAKER,
            AudioRoute.PHONE to CallAudioState.ROUTE_WIRED_OR_EARPIECE,
            AudioRoute.BLUETOOTH to CallAudioState.ROUTE_BLUETOOTH,
        )

        for ((route, nativeRoute) in expected) {
            audio.routeAudio(route)

            verify { connection.setAudioRoute(nativeRoute) }
        }
        verify(exactly = 3) { events.broadcast(AudioStateUpdated::class) }
    }

    @Test
    fun `nothing is routed without a connection`() {
        every { androidCallFramework.connection } returns null

        audio.routeAudio(AudioRoute.SPEAKER)

        verify(exactly = 0) { events.broadcast(AudioStateUpdated::class) }
    }

    @Test
    fun `the speaker uses the echo limiter instead of echo cancellation`() {
        val call = voipLibCall()
        calls.add(call)
        every { call.linphoneCall.core.isEchoCancellationEnabled } returns true

        audio.routeAudio(AudioRoute.SPEAKER)
        verify {
            call.linphoneCall.isEchoLimiterEnabled = true
            call.linphoneCall.isEchoCancellationEnabled = false
        }

        audio.routeAudio(AudioRoute.PHONE)
        verify {
            call.linphoneCall.isEchoLimiterEnabled = false
            call.linphoneCall.isEchoCancellationEnabled = true
        }
    }

    @Test
    fun `the speaker volume is nudged a second after routing to it`() {
        every { androidAudioManager.getStreamVolume(AndroidAudioManager.STREAM_VOICE_CALL) } returns 3
        every { androidAudioManager.getStreamMaxVolume(AndroidAudioManager.STREAM_VOICE_CALL) } returns 7

        audio.routeAudio(AudioRoute.SPEAKER)
        shadowOf(Looper.getMainLooper()).idleFor(999, TimeUnit.MILLISECONDS)
        verify(exactly = 0) { androidAudioManager.setStreamVolume(any(), any(), any()) }

        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        verify { androidAudioManager.setStreamVolume(AndroidAudioManager.STREAM_VOICE_CALL, 4, 0) }
    }

    @Test
    fun `the speaker volume is nudged down at its maximum`() {
        every { androidAudioManager.getStreamVolume(AndroidAudioManager.STREAM_VOICE_CALL) } returns 7
        every { androidAudioManager.getStreamMaxVolume(AndroidAudioManager.STREAM_VOICE_CALL) } returns 7

        audio.routeAudio(AudioRoute.SPEAKER)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        verify { androidAudioManager.setStreamVolume(AndroidAudioManager.STREAM_VOICE_CALL, 6, 0) }
    }

    @Test
    fun `the volume is left alone for other routes`() {
        audio.routeAudio(AudioRoute.PHONE)
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)

        verify(exactly = 0) { androidAudioManager.setStreamVolume(any(), any(), any()) }
    }

    @Test
    fun `routing to a bluetooth device requests it and switches to bluetooth`() {
        audio.routeAudio(BluetoothAudioRoute("Car kit", "Car kit"))

        verify {
            connection.requestBluetoothAudio(carKit)
            connection.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
        }
    }

    @Test
    fun `routing to a bluetooth device while on bluetooth only requests the device`() {
        every { callAudioState.route } returns CallAudioState.ROUTE_BLUETOOTH

        audio.routeAudio(BluetoothAudioRoute("Car kit", "Car kit"))

        verify { connection.requestBluetoothAudio(carKit) }
        verify(exactly = 0) { connection.setAudioRoute(any()) }
    }

    @Test
    fun `routing to a bluetooth device needs the bluetooth permission`() {
        shadowOf(application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        audio.routeAudio(BluetoothAudioRoute("Car kit", "Car kit"))

        verify(exactly = 0) {
            connection.requestBluetoothAudio(any())
            connection.setAudioRoute(any())
        }
    }

    @Test
    fun `muting is set on the voip lib and announced`() {
        var muted = false
        every { voipLib.microphoneMuted } answers { muted }
        every { voipLib.microphoneMuted = any() } answers { muted = firstArg() }

        audio.mute()
        assertTrue(audio.isMicrophoneMuted)

        audio.toggleMute()
        assertFalse(audio.isMicrophoneMuted)

        audio.toggleMute()
        audio.unmute()
        assertFalse(audio.isMicrophoneMuted)

        verify(exactly = 4) { events.broadcast(AudioStateUpdated::class) }
    }

    private fun assertDefault(state: AudioState) {
        assertEquals(AudioRoute.PHONE, state.currentRoute)
        assertEquals(0, state.availableRoutes.size)
        assertNull(state.bluetoothDeviceName)
        assertFalse(state.isMicrophoneMuted)
        assertEquals(0, state.bluetoothRoutes.size)
    }

    private fun bluetoothDevice(name: String?) = mockk<BluetoothDevice> { every { this@mockk.name } returns name }
}
