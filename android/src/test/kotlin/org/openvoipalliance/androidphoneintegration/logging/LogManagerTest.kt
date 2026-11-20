package org.openvoipalliance.androidphoneintegration.logging

import io.mockk.every
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.PIL
import org.openvoipalliance.androidphoneintegration.applicationSetup
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.debug.VersionInfo
import org.openvoipalliance.androidphoneintegration.log
import org.openvoipalliance.androidphoneintegration.logWithContext
import org.openvoipalliance.androidphoneintegration.mockPil
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.openvoipalliance.voiplib.repository.initialize.LogLevel as VoipLibLogLevel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LogManagerTest {
    private val logs = mutableListOf<Pair<String, LogLevel>>()
    private val pil = mockPil(applicationSetup().copy(logger = { message, level -> logs += message to level }))
    private val logManager = LogManager(pil)

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `logs are written to the app's logger`() {
        logManager.writeLog("message", LogLevel.WARNING)
        logManager.writeLog("default")

        assertEquals(listOf("message" to LogLevel.WARNING, "default" to LogLevel.INFO), logs)
    }

    @Test
    fun `logs are dropped without a logger`() {
        LogManager(mockPil()).writeLog("message")
    }

    @Test
    fun `voip lib log levels map to PIL log levels`() {
        val expected = mapOf(
            VoipLibLogLevel.DEBUG to LogLevel.DEBUG,
            VoipLibLogLevel.TRACE to LogLevel.DEBUG,
            VoipLibLogLevel.MESSAGE to LogLevel.INFO,
            VoipLibLogLevel.WARNING to LogLevel.WARNING,
            VoipLibLogLevel.ERROR to LogLevel.ERROR,
            VoipLibLogLevel.FATAL to LogLevel.ERROR,
        )

        expected.keys.forEach { logManager.onLogMessageWritten(it, it.name) }

        assertEquals(expected.map { (from, to) -> from.name to to }, logs)
    }

    @Test
    fun `the version is logged`() {
        every { pil.versionInfo } returns VersionInfo("pil", "5.5.35")

        logManager.logVersion()

        assertEquals(
            listOf(
                "Version Info: Android Phone Integration Lib: pil | Underlying VoIP Library: 5.5.35" to LogLevel.INFO,
            ),
            logs,
        )
    }

    @Test
    fun `the log helpers write through the PIL with their context`() {
        log("plain", LogLevel.ERROR)
        logWithContext("message", "CONTEXT")

        io.mockk.verify {
            pil.writeLog("plain", LogLevel.ERROR)
            pil.writeLog("CONTEXT: message", LogLevel.INFO)
        }
    }

    @Test
    fun `the log helpers do nothing without a PIL`() {
        clearPilInstance()

        log("dropped")

        assertEquals(false, PIL.isInitialized)
    }
}
