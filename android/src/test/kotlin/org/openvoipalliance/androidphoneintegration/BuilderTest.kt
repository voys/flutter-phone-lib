package org.openvoipalliance.androidphoneintegration

import android.app.NotificationManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.call.CallFactory
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.androidphoneintegration.configuration.Preferences
import org.openvoipalliance.androidphoneintegration.contacts.Contacts
import org.openvoipalliance.androidphoneintegration.di.di
import org.openvoipalliance.androidphoneintegration.exception.PILAlreadyInitializedException
import org.openvoipalliance.androidphoneintegration.notifications.CallNotification
import org.openvoipalliance.androidphoneintegration.notifications.IncomingCallNotification
import org.openvoipalliance.androidphoneintegration.notifications.IncomingCallRinger
import org.openvoipalliance.androidphoneintegration.telecom.Connection
import org.openvoipalliance.voiplib.repository.Dns
import org.openvoipalliance.voiplib.repository.call.controls.LinphoneSipActiveCallControlsRepository
import org.openvoipalliance.voiplib.repository.call.session.LinphoneSipSessionRepository
import org.openvoipalliance.voiplib.repository.registration.LinphoneSipRegisterRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/** Starts the PIL with its real dependency graph, only linphone's native factory is mocked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BuilderTest {
    private val auth = Auth("user", "password", "sip.example.com", 5061, true)
    private val preferences = Preferences(useApplicationProvidedRingtone = true, enableAdvancedLogging = true)

    @Before
    fun setUp() = allowRealPilModuleToLoad()

    @After
    fun tearDown() {
        clearPilInstance()
        restoreLinphoneFactory()
    }

    @Test
    fun `starting creates the PIL with the builder's preferences and auth`() {
        mockLinphoneFactory()

        val pil = startAndroidPIL {
            preferences = this@BuilderTest.preferences
            auth = this@BuilderTest.auth
            applicationSetup()
        }

        assertSame(pil, PIL.instance)
        assertEquals(preferences, pil.preferences)
        assertEquals(auth, pil.auth)
    }

    @Test
    fun `every dependency in the PIL module resolves`() {
        mockLinphoneFactory()
        val pil = startAndroidPIL { applicationSetup() }

        listOf(
            pil.actions, pil.audio, pil.events, pil.calls, pil.voipLib, pil.phoneLibHelper,
            pil.androidCallFramework, pil.platformIntegrator, pil.logManager, pil.notifications,
            pil.voipLibEventTranslator, pil.telephonyManager,
        ).forEach(::assertNotNull)

        val koin = di.koin
        listOf(
            CallFactory::class, Contacts::class, Connection::class, NotificationManager::class,
            android.media.AudioManager::class, IncomingCallNotification::class,
            IncomingCallRinger::class, CallNotification::class, Dns::class,
            LinphoneSipRegisterRepository::class, LinphoneSipActiveCallControlsRepository::class,
            LinphoneSipSessionRepository::class,
        ).forEach {
            @Suppress("UNCHECKED_CAST")
            assertNotNull(it.simpleName, koin.get(it as KClass<Any>))
        }
        assertSame(pil, koin.get(PIL::class))
    }

    @Test
    fun `the builder defaults to no auth and default preferences`() {
        mockLinphoneFactory()

        val pil = startAndroidPIL { applicationSetup() }

        assertNull(pil.auth)
        assertEquals(Preferences.DEFAULT, pil.preferences)
    }

    @Test
    fun `the PIL can only be started once`() {
        mockPil()

        assertThrows(PILAlreadyInitializedException::class.java) {
            startAndroidPIL { applicationSetup() }
        }
    }
}
