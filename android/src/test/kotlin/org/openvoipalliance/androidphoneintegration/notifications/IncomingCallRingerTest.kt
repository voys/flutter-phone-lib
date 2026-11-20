package org.openvoipalliance.androidphoneintegration.notifications

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.configuration.Preferences
import org.openvoipalliance.androidphoneintegration.mockPil
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowMediaPlayer.MediaInfo
import org.robolectric.shadows.ShadowMediaPlayer.State
import org.robolectric.shadows.util.DataSource
import java.io.IOException
import android.media.AudioManager as AndroidAudioManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IncomingCallRingerTest {
    private val pil = mockPil()
    private val audioManager = mockk<AndroidAudioManager>(relaxed = true)
    private val ringer = IncomingCallRinger(application, pil, audioManager)
    private val players = mutableListOf<ShadowMediaPlayer>()
    private val mediaPlayers = mutableListOf<MediaPlayer>()
    private val appRingtone = Uri.parse("android.resource://${application.packageName}/raw/ringtone")

    @Before
    fun setUp() {
        every { pil.preferences } returns Preferences.DEFAULT
        every { audioManager.ringerMode } returns AndroidAudioManager.RINGER_MODE_NORMAL
        every { audioManager.getDevices(AndroidAudioManager.GET_DEVICES_OUTPUTS) } returns emptyArray()
        ShadowMediaPlayer.setCreateListener { player, shadow ->
            mediaPlayers += player
            players += shadow
        }
        ShadowMediaPlayer.addMediaInfo(dataSource(Settings.System.DEFAULT_RINGTONE_URI), MediaInfo(1000, 0))
        ShadowMediaPlayer.addMediaInfo(dataSource(appRingtone), MediaInfo(1000, 0))
    }

    @After
    fun tearDown() {
        ShadowMediaPlayer.resetStaticState()
        DataSource.reset()
        clearPilInstance()
    }

    @Test
    fun `the default ringtone loops as a ringtone once audio focus is requested`() {
        val focus = slot<AudioFocusRequest>()

        ringer.start()
        shadowOf(Looper.getMainLooper()).idle()

        verify { audioManager.requestAudioFocus(capture(focus)) }
        assertEquals(AndroidAudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, focus.captured.focusGain)
        val player = players.single()
        assertEquals(dataSource(Settings.System.DEFAULT_RINGTONE_URI), player.dataSource)
        assertTrue(mediaPlayers.single().isLooping)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION_RINGTONE, player.audioAttributes.usage)
        assertEquals(State.STARTED, player.state)
    }

    @Test
    fun `the app's own ringtone is used when preferred`() {
        every { pil.preferences } returns Preferences(useApplicationProvidedRingtone = true)

        ringer.start()

        assertEquals(dataSource(appRingtone), players.single().dataSource)
    }

    @Test
    fun `nothing rings on silent or vibrate`() {
        for (mode in listOf(AndroidAudioManager.RINGER_MODE_SILENT, AndroidAudioManager.RINGER_MODE_VIBRATE)) {
            every { audioManager.ringerMode } returns mode

            ringer.start()
        }

        assertTrue(players.isEmpty())
        verify(exactly = 0) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
    }

    @Test
    fun `starting while ringing changes nothing`() {
        ringer.start()
        ringer.start()

        assertEquals(1, players.size)
    }

    @Test
    fun `stopping releases the player and audio focus`() {
        ringer.start()
        shadowOf(Looper.getMainLooper()).idle()

        ringer.stop()

        assertEquals(State.END, players.single().state)
        verify { audioManager.abandonAudioFocusRequest(any()) }

        ringer.start()
        assertEquals(2, players.size)
    }

    @Test
    fun `stopping without ringing only abandons audio focus`() {
        ringer.stop()

        verify { audioManager.abandonAudioFocusRequest(any()) }
    }

    @Test
    fun `an unusable ringtone plays nothing`() {
        ShadowMediaPlayer.addException(dataSource(Settings.System.DEFAULT_RINGTONE_URI), IOException())

        ringer.start()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(State.IDLE, players.single().state)
    }

    // Robolectric doesn't record MediaPlayer.setPreferredDevice, so the log is all there is to see.
    @Test
    fun `a connected bluetooth device is preferred for the ringtone`() {
        val bluetooth = mockk<AudioDeviceInfo>(relaxed = true) {
            every { type } returns AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            every { productName } returns "Headset"
        }
        every { audioManager.getDevices(AndroidAudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(bluetooth)

        ringer.start()
        shadowOf(Looper.getMainLooper()).idle()

        verify {
            pil.writeLog(
                "INCOMING-CALL-RINGER: Bluetooth device already connected, routing ringtone directly to it: Headset",
                any(),
            )
        }
        assertEquals(State.STARTED, players.single().state)
    }

    private fun dataSource(uri: Uri) = DataSource.toDataSource(application, uri)
}
