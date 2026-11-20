package org.openvoipalliance.voiplib.repository.registration

import android.os.Looper
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.Account
import org.linphone.core.AccountParams
import org.linphone.core.Address
import org.linphone.core.AuthInfo
import org.linphone.core.Core
import org.linphone.core.CoreListener
import org.linphone.core.TransportType
import org.openvoipalliance.androidphoneintegration.PIL
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.androidphoneintegration.mockLinphoneFactory
import org.openvoipalliance.androidphoneintegration.restoreLinphoneFactory
import org.openvoipalliance.voiplib.model.RegistrationState
import org.openvoipalliance.voiplib.repository.LinphoneCoreInstanceManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import org.linphone.core.RegistrationState as LinphoneRegistrationState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LinphoneSipRegisterRepositoryTest {
    private val factory = mockLinphoneFactory()
    private val core = mockk<Core>(relaxed = true)
    private val manager = mockk<LinphoneCoreInstanceManager> { every { safeLinphoneCore } returns core }
    private var auth: Auth? = Auth("user", "password", "sip.example.com", 5061, true)
    private val pil = mockk<PIL> { every { auth } answers { this@LinphoneSipRegisterRepositoryTest.auth } }
    private val repository = LinphoneSipRegisterRepository(pil, manager)

    private val listener = slot<CoreListener>()
    private val params = mockk<AccountParams>(relaxed = true)
    private val account = mockk<Account>(relaxed = true)
    private val identity = mockk<Address>()
    private val server = mockk<Address>()
    private val authInfo = mockk<AuthInfo>()
    private val results = mutableListOf<RegistrationState>()

    @Before
    fun setUp() {
        every { core.addListener(capture(listener)) } just runs
        every { core.accountList } returns emptyArray()
        every { core.createAccountParams() } returns params
        every { core.createAccount(params) } returns account
        every { core.addAccount(account) } returns 0
        every { account.params.clone() } returns mockk(relaxed = true)
        every { core.interpretUrl("sip:user@sip.example.com:5061", false) } returns identity
        every { core.interpretUrl("sip:sip.example.com;transport=tls", false) } returns server
        every {
            factory.createAuthInfo("user", "user", "password", null, null, "sip.example.com")
        } returns authInfo
    }

    @After
    fun tearDown() = restoreLinphoneFactory()

    @Test
    fun `registering fails without a core or auth`() {
        every { manager.safeLinphoneCore } returns null
        repository.register { results += it }

        every { manager.safeLinphoneCore } returns core
        auth = null
        repository.register { results += it }

        assertEquals(listOf(RegistrationState.FAILED, RegistrationState.FAILED), results)
        verify(exactly = 0) { core.addAccount(any()) }
    }

    @Test
    fun `the first registration adds a TLS account with its credentials`() {
        repository.register { results += it }

        verify {
            params.identityAddress = identity
            params.isRegisterEnabled = true
            params.serverAddress = server
            params.transport = TransportType.Tls
            core.addAccount(account)
            core.addAuthInfo(authInfo)
            core.defaultAccount = account
        }
        assertTrue(results.isEmpty())
    }

    @Test
    fun `registering processes a placeholder push notification`() {
        repository.register { results += it }

        verify { core.processPushNotification("asd") }
    }

    @Test
    fun `an existing account is refreshed instead of added again`() {
        every { core.accountList } returns arrayOf(account)

        repository.register { results += it }

        verify {
            core.refreshRegisters()
            core.iterate()
        }
        verify(exactly = 0) { core.addAccount(any()) }
    }

    @Test
    fun `an invalid port can't be registered`() {
        for (port in listOf(0, 65536)) {
            auth = auth!!.copy(port = port)

            assertThrows(IllegalArgumentException::class.java) { repository.register { results += it } }
        }
    }

    @Test
    fun `an account linphone won't add fails the registration`() {
        every { core.addAccount(account) } returns -1

        repository.register { results += it }
        stateChanged(LinphoneRegistrationState.Ok)

        assertEquals(listOf(RegistrationState.FAILED), results)
        verify(exactly = 0) { core.addAuthInfo(any()) }
    }

    @Test
    fun `changed credentials unregister the old account first`() {
        repository.register { }
        verify(exactly = 0) { core.clearAccounts() }

        auth = auth!!.copy(password = "new-password")
        repository.register { }

        verify {
            core.clearAccounts()
            core.clearAllAuthInfo()
        }
    }

    @Test
    fun `a successful registration is reported once`() {
        repository.register { results += it }

        stateChanged(LinphoneRegistrationState.Ok)
        stateChanged(LinphoneRegistrationState.Ok)

        assertEquals(listOf(RegistrationState.REGISTERED), results)
    }

    @Test
    fun `an early failure is not reported, as success may follow`() {
        repository.register { results += it }

        stateChanged(LinphoneRegistrationState.Progress)
        stateChanged(LinphoneRegistrationState.Failed)
        stateChanged(LinphoneRegistrationState.Ok)

        assertEquals(listOf(RegistrationState.REGISTERED), results)
    }

    // The follow-up meant to report the timeout without further updates cancels itself as it is
    // scheduled, so only another update past the timeout reports it.
    @Test
    fun `a registration that doesn't succeed in time fails on the next update`() {
        repository.register { results += it }

        stateChanged(LinphoneRegistrationState.Progress)
        Thread.sleep(LinphoneSipRegisterRepository.REGISTRATION_TIMEOUT_MS + 1500)
        assertTrue(results.isEmpty())

        stateChanged(LinphoneRegistrationState.Progress)
        assertEquals(listOf(RegistrationState.FAILED), results)
        verify { core.clearAccounts() }
    }

    @Test
    fun `unregistering without an account completes straight away`() {
        every { core.defaultAccount } returns null
        var completed = 0

        repository.unregisterAndWait { completed++ }

        assertEquals(1, completed)
    }

    @Test
    fun `unregistering waits until the server clears the account`() {
        val disabled = mockk<AccountParams>(relaxed = true)
        every { core.defaultAccount } returns account
        every { account.params.clone() } returns disabled
        repository.register { results += it }
        var completed = 0

        repository.unregisterAndWait { completed++ }
        verify {
            disabled.isRegisterEnabled = false
            account.params = disabled
        }

        stateChanged(LinphoneRegistrationState.Progress)
        assertEquals(0, completed)

        stateChanged(LinphoneRegistrationState.Cleared)
        stateChanged(LinphoneRegistrationState.Cleared)
        assertEquals(1, completed)
        assertTrue("Unregistering doesn't report to the registration callback", results.isEmpty())
    }

    @Test
    fun `unregistering completes when the server fails it`() {
        every { core.defaultAccount } returns account
        repository.register { }
        var completed = 0

        repository.unregisterAndWait { completed++ }
        stateChanged(LinphoneRegistrationState.Failed)

        assertEquals(1, completed)
    }

    @Test
    fun `unregistering completes when the server doesn't answer in time`() {
        every { core.defaultAccount } returns account
        repository.register { }
        var completed = 0

        repository.unregisterAndWait { completed++ }
        shadowOf(Looper.getMainLooper()).idleFor(
            LinphoneSipRegisterRepository.UNREGISTER_TIMEOUT_MS - 1,
            TimeUnit.MILLISECONDS,
        )
        assertEquals(0, completed)

        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        stateChanged(LinphoneRegistrationState.Cleared)
        assertEquals(1, completed)
    }

    @Test
    fun `unregistering again while waiting completes both once`() {
        every { core.defaultAccount } returns account
        repository.register { }
        val completed = mutableListOf<String>()

        repository.unregisterAndWait { completed += "first" }
        repository.unregisterAndWait { completed += "second" }
        stateChanged(LinphoneRegistrationState.Cleared)

        assertEquals(listOf("first", "second"), completed)
        verify(exactly = 1) { account.params = any() }
    }

    @Test
    fun `unregister clears accounts and credentials`() {
        repository.unregister()

        verify {
            core.clearAccounts()
            core.clearAllAuthInfo()
        }
    }

    private fun stateChanged(state: LinphoneRegistrationState) =
        listener.captured.onAccountRegistrationStateChanged(core, account, state, "")
}
