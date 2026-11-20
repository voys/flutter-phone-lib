package org.openvoipalliance.voiplib.repository.call.session

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.Address
import org.linphone.core.CallParams
import org.linphone.core.Core
import org.linphone.core.CoreException
import org.linphone.core.Reason
import org.openvoipalliance.androidphoneintegration.PIL
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.voiplib.model.Call
import org.openvoipalliance.voiplib.repository.LinphoneCoreInstanceManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.linphone.core.Call as LinphoneCall

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LinphoneSipSessionRepositoryTest {
    private val core = mockk<Core>(relaxed = true)
    private val manager = mockk<LinphoneCoreInstanceManager> {
        every { safeLinphoneCore } returns core
        every { state.initialized } returns true
    }
    private val pil = mockk<PIL> {
        every { auth } returns Auth("user", "password", "sip.example.com", 5061, true)
    }
    private val repository = LinphoneSipSessionRepository(pil, manager)
    private val linphoneCall = mockk<LinphoneCall>(relaxed = true)
    private val address = mockk<Address>()
    private val params = mockk<CallParams>(relaxed = true)

    @Before
    fun setUp() {
        every { core.interpretUrl("+31501234567@sip.example.com") } returns address
        every { core.createCallParams(null) } returns params
        every { core.inviteAddressWithParams(address, params) } returns linphoneCall
    }

    @Test
    fun `accepting and declining go through linphone`() {
        repository.acceptIncoming(Call(linphoneCall))
        repository.declineIncoming(Call(linphoneCall), Reason.Busy)

        verify {
            linphoneCall.accept()
            linphoneCall.decline(Reason.Busy)
        }
    }

    @Test
    fun `linphone errors while accepting or declining are swallowed`() {
        every { linphoneCall.accept() } throws CoreException("accept")
        every { linphoneCall.decline(any()) } throws CoreException("decline")

        repository.acceptIncoming(Call(linphoneCall))
        repository.declineIncoming(Call(linphoneCall), Reason.Declined)
    }

    @Test
    fun `calling invites number at the auth domain without video`() {
        val call = repository.callTo("+31501234567")

        assertSame(linphoneCall, call.linphoneCall)
        verify { params.isVideoEnabled = false }
    }

    @Test
    fun `calling needs linphone to be ready`() {
        every { manager.state.initialized } returns false

        val error = assertThrows(Exception::class.java) { repository.callTo("+31501234567") }
        assertEquals("Linphone is not ready", error.message)
    }

    @Test
    fun `calling needs a number and auth`() {
        assertEquals(
            "Phone number is not valid",
            assertThrows(IllegalArgumentException::class.java) { repository.callTo("") }.message,
        )

        every { pil.auth } returns null
        assertEquals(
            "No authentication",
            assertThrows(IllegalArgumentException::class.java) { repository.callTo("+31501234567") }.message,
        )
    }

    @Test
    fun `calling fails when linphone can't place the call`() {
        fun assertCallFails() = assertEquals(
            "Call failed",
            assertThrows(Exception::class.java) { repository.callTo("+31501234567") }.message,
        )

        every { core.inviteAddressWithParams(any(), any()) } returns null
        assertCallFails()

        every { core.createCallParams(null) } returns null
        assertCallFails()

        every { core.interpretUrl(any<String>()) } throws CoreException("bad url")
        assertCallFails()

        every { manager.safeLinphoneCore } returns null
        assertCallFails()
    }

    @Test
    fun `ending a call also ends a conference`() {
        every { core.isInConference } returns false
        repository.end(Call(linphoneCall))
        verify(exactly = 1) { linphoneCall.terminate() }
        verify(exactly = 0) { core.terminateConference() }

        every { core.isInConference } returns true
        repository.end(Call(linphoneCall))
        verify(exactly = 1) { core.terminateConference() }
    }

    @Test
    fun `connection parameters form a user at host url`() {
        assertEquals(
            "201@sip.example.com",
            LinphoneSipSessionRepository.ConnectionParameters("201", "sip.example.com").asUrl(),
        )
    }
}
