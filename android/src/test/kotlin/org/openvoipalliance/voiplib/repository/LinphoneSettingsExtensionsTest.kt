package org.openvoipalliance.voiplib.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.linphone.core.Config
import org.linphone.core.Core

/** These settings have no linphone API, so the exact config section and key are what matter. */
class LinphoneSettingsExtensionsTest {
    private val config = mockk<Config>(relaxed = true)
    private val core = mockk<Core> { every { config } returns this@LinphoneSettingsExtensionsTest.config }

    private class Setting(
        val section: String,
        val key: String,
        val set: Core.(Boolean) -> Unit,
        val get: Core.() -> Boolean,
    )

    @Test
    fun `boolean settings are written to and read from their config keys`() {
        val settings = listOf(
            Setting("sip", "auto_net_state_mon", { automaticNetworkStateMonitoring = it }, { automaticNetworkStateMonitoring }),
            Setting("audio", "android_pause_calls_when_audio_focus_lost", { pauseCallsWhenAudioFocusLost = it }, { pauseCallsWhenAudioFocusLost }),
            Setting("sip", "auto_answer_replacing_calls", { autoAnswerReplacingCalls = it }, { autoAnswerReplacingCalls }),
            Setting("sip", "ping_with_options", { pingWithOptions = it }, { pingWithOptions }),
            Setting("audio", "android_disable_audio_focus_requests", { disableAudioFocusRequests = it }, { disableAudioFocusRequests }),
        )

        for (setting in settings) {
            setting.set(core, true)
            verify { config.setBool(setting.section, setting.key, true) }

            every { config.getBool(setting.section, setting.key, false) } returns true
            assertTrue(setting.key, setting.get(core))
        }
    }

    @Test
    fun `registerOnlyWhenNetworkIsUp is linphone's own setting, not the config extension`() {
        every { core.registerOnlyWhenNetworkIsUp = any() } returns Unit

        core.registerOnlyWhenNetworkIsUp = true

        verify { core.registerOnlyWhenNetworkIsUp = true }
        verify(exactly = 0) { config.setBool(any(), any(), any()) }
    }

    @Test
    fun `keep alive period is written to and read from its config key`() {
        core.keepAlivePeriod = 30000
        verify { config.setInt("sip", "keepalive_period", 30000) }

        every { config.getInt("sip", "keepalive_period", 0) } returns 30000
        assertEquals(30000, core.keepAlivePeriod)
    }
}
