package org.openvoipalliance.androidphoneintegration.debug

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.flutterphonelib.R
import org.openvoipalliance.voiplib.VoIPLib
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VersionInfoTest {
    private val voipLib = mockk<VoIPLib>()
    private val pilVersion = application.getString(R.string.pil_build_info_tag)

    @Test
    fun `the voip version is reported once initialized`() {
        every { voipLib.isInitialized } returns true
        every { voipLib.version } returns "5.5.35"

        assertEquals(VersionInfo(pilVersion, "5.5.35"), VersionInfo.build(application, voipLib))
    }

    @Test
    fun `the voip version is not initialized before then`() {
        every { voipLib.isInitialized } returns false

        assertEquals(VersionInfo(pilVersion, "Not initialized"), VersionInfo.build(application, voipLib))
    }

    @Test
    fun `version info describes both versions`() {
        assertEquals(
            "Version Info: Android Phone Integration Lib: 1.0 | Underlying VoIP Library: 5.5.35",
            VersionInfo("1.0", "5.5.35").toString(),
        )
    }
}
