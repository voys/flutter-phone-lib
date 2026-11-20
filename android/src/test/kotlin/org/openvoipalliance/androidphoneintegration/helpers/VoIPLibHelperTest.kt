package org.openvoipalliance.androidphoneintegration.helpers

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.voiplib.RegistrationCallback
import org.openvoipalliance.voiplib.VoIPLib
import org.openvoipalliance.voiplib.model.RegistrationState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoIPLibHelperTest {
    private val pil = mockPil()
    private val voipLib = mockk<VoIPLib>(relaxed = true)
    private val helper = VoIPLibHelper(pil, voipLib)
    private val registration = slot<RegistrationCallback>()
    private val results = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        every { pil.auth } returns Auth("user", "password", "sip.example.com", 5061, true)
        every { voipLib.register(capture(registration)) } returns voipLib
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `registration needs valid auth`() {
        every { pil.auth } returns null

        helper.register { results += it }

        assertEquals(listOf(false), results)
        verify(exactly = 0) { voipLib.register(any()) }
    }

    @Test
    fun `only the final registration state is reported`() {
        helper.register { results += it }

        registration.captured(RegistrationState.PROGRESS)
        registration.captured(RegistrationState.REGISTERED)
        registration.captured(RegistrationState.NONE)
        registration.captured(RegistrationState.FAILED)

        assertEquals(listOf(true, false), results)
    }

    @Test
    fun `push notifications are passed to the voip lib`() {
        helper.processPushNotification("call-id")

        verify { voipLib.processPushNotification("call-id") }
    }
}
