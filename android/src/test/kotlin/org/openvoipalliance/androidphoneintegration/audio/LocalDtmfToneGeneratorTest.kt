package org.openvoipalliance.androidphoneintegration.audio

import android.media.ToneGenerator
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToneGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalDtmfToneGeneratorTest {
    private val generator = LocalDtmfToneGenerator(application)

    @After
    fun tearDown() = ShadowToneGenerator.reset()

    @Test
    fun `each keypad character plays its DTMF tone`() {
        val expected = mapOf(
            '0' to ToneGenerator.TONE_DTMF_0,
            '1' to ToneGenerator.TONE_DTMF_1,
            '2' to ToneGenerator.TONE_DTMF_2,
            '3' to ToneGenerator.TONE_DTMF_3,
            '4' to ToneGenerator.TONE_DTMF_4,
            '5' to ToneGenerator.TONE_DTMF_5,
            '6' to ToneGenerator.TONE_DTMF_6,
            '7' to ToneGenerator.TONE_DTMF_7,
            '8' to ToneGenerator.TONE_DTMF_8,
            '9' to ToneGenerator.TONE_DTMF_9,
            '#' to ToneGenerator.TONE_DTMF_P,
            '*' to ToneGenerator.TONE_DTMF_S,
        )

        expected.keys.forEach(generator::play)

        val played = ShadowToneGenerator.getPlayedTones()
        assertEquals(expected.values.toList(), played.map { it.type() })
        played.forEach { assertEquals(150, it.duration().toMillis().toInt()) }
    }

    @Test
    fun `other characters play nothing`() {
        generator.play('A')
        generator.play(',')

        assertEquals(0, ShadowToneGenerator.getPlayedTones().size)
    }

    @Test
    fun `nothing plays when the user turned off dial pad tones`() {
        Settings.System.putInt(application.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 0)

        generator.play('1')

        assertEquals(0, ShadowToneGenerator.getPlayedTones().size)
    }
}
