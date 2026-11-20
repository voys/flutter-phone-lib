package org.openvoipalliance.androidphoneintegration.configuration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.TestCallActivity
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.isNullOrInvalid
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConfigurationTest {
    private val auth = Auth("user", "password", "sip.example.com", 5061, true)

    @Test
    fun `auth needs a username, password, domain and port`() {
        assertTrue(auth.isValid)
        assertFalse(auth.copy(username = " ").isValid)
        assertFalse(auth.copy(password = "").isValid)
        assertFalse(auth.copy(domain = "").isValid)
        assertFalse(auth.copy(port = 0).isValid)
    }

    @Test
    fun `missing auth counts as invalid`() {
        assertTrue(null.isNullOrInvalid)
        assertTrue(auth.copy(password = "").isNullOrInvalid)
        assertFalse(auth.isNullOrInvalid)
    }

    @Test
    fun `preferences are equal when their settings are`() {
        assertEquals(Preferences(useApplicationProvidedRingtone = false), Preferences.DEFAULT)
        assertFalse(Preferences.DEFAULT.enableAdvancedLogging)
        assertEquals(
            Preferences(true, true).hashCode(),
            Preferences(true, true).hashCode(),
        )
        assertNotEquals(Preferences(true, false), Preferences(true, true))
        assertNotEquals(Preferences(false, true), Preferences(true, true))
        assertFalse(Preferences.DEFAULT.equals("not preferences"))
    }

    @Test
    fun `application setup defaults`() {
        val setup = ApplicationSetup(
            application,
            ApplicationSetup.Activities(TestCallActivity::class.java, null),
        )

        assertNull(setup.middleware)
        assertNull(setup.logger)
        assertEquals(ApplicationSetup.AutomaticallyLaunchCallActivity.ALWAYS, setup.automaticallyLaunchCallActivity)
        assertEquals("AndroidPIL", setup.userAgent)
        assertTrue(setup.notifyOnMissedCall)
        assertNull(setup.onMissedCallNotificationPressed)
        assertEquals("unique_key", setup.pushMessageCallIdKey)
    }
}
