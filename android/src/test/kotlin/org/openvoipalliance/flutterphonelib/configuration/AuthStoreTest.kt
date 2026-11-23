package org.openvoipalliance.flutterphonelib.configuration

import android.content.Context
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import org.openvoipalliance.flutterphonelib.FakePasswordStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuthStoreTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("AuthStoreTest", Context.MODE_PRIVATE)
    private val passwords = FakePasswordStore()
    private val store = AuthStore(prefs, passwords)
    private val auth = Auth("user", "secret", "sip.example.com", 5061, true)

    private val persisted: Auth?
        get() = Gson().fromJson(prefs.getString(AuthStore.KEY, null), Auth::class.java)

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `the password is kept out of the preferences`() {
        store.save(auth)

        assertEquals(auth.copy(password = ""), persisted)
        assertEquals("secret", passwords.password)
    }

    @Test
    fun `the account is loaded with its password`() {
        store.save(auth)

        assertEquals(auth, store.load())
    }

    @Test
    fun `there is no account before one is saved`() {
        assertNull(store.load())
    }

    @Test
    fun `an account without its password can't be loaded`() {
        store.save(auth)
        passwords.password = null

        assertNull(store.load())
    }

    @Test
    fun `an account saved by an older version is loaded and its password moved`() {
        prefs.edit().putString(AuthStore.KEY, Gson().toJson(auth)).commit()

        assertEquals(auth, store.load())
        assertEquals(auth.copy(password = ""), persisted)
        assertEquals("secret", passwords.password)
    }

    @Test
    fun `the password stays in the preferences when it can't be stored securely`() {
        passwords.failure = IllegalStateException("keystore")

        store.save(auth)

        assertEquals(auth, persisted)
        assertEquals(auth, store.load())
    }

    @Test
    fun `a password that couldn't be stored securely is moved on the next load`() {
        passwords.failure = IllegalStateException("keystore")
        store.save(auth)
        passwords.failure = null

        store.load()

        assertEquals(auth.copy(password = ""), persisted)
        assertEquals("secret", passwords.password)
    }

    @Test
    fun `the password in the preferences is newer than the one in secure storage`() {
        passwords.password = "old"
        prefs.edit().putString(AuthStore.KEY, Gson().toJson(auth)).commit()

        assertEquals(auth, store.load())
        assertEquals("secret", passwords.password)
    }

    @Test
    fun `an unreadable secure storage is the same as having no password`() {
        store.save(auth)
        passwords.failure = IllegalStateException("keystore")

        assertNull(store.load())
    }

    @Test
    fun `clearing removes the account and its password`() {
        store.save(auth)

        store.clear()

        assertNull(persisted)
        assertNull(passwords.password)
    }

    @Test
    fun `clearing removes the account even when the password can't be removed`() {
        store.save(auth)
        passwords.failure = IllegalStateException("keystore")

        store.clear()

        assertNull(persisted)
    }
}
