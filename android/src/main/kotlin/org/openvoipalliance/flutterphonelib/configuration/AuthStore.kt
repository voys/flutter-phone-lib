package org.openvoipalliance.flutterphonelib.configuration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import org.openvoipalliance.androidphoneintegration.configuration.Auth
import co.voys.sharedsecurestorage.SharedSecureStorage

/**
 * Keeps the SIP account for a native start, with the password in secure storage instead of in
 * plain text.
 */
internal class AuthStore(
    private val prefs: SharedPreferences,
    private val passwords: PasswordStore,
) {
    fun save(auth: Auth) {
        val isPasswordSecured = runCatching { passwords.write(auth.password) }
            .onFailure { Log.w(LOG_TAG, "Unable to store the SIP password securely", it) }
            .isSuccess

        // Without a password there would be no native start anymore, so it then stays in plain
        // text like it used to. The next load moves it.
        val persisted = if (isPasswordSecured) auth.copy(password = "") else auth

        prefs.edit().putString(KEY, Gson().toJson(persisted)).apply()
    }

    fun load(): Auth? {
        val auth = Gson().fromJson(prefs.getString(KEY, null), Auth::class.java) ?: return null

        // Stored by a version that kept it in plain text, or it couldn't be secured before.
        if (auth.password.isNotBlank()) {
            save(auth)
            return auth
        }

        val password = runCatching { passwords.read() }
            .onFailure { Log.w(LOG_TAG, "Unable to read the SIP password", it) }
            .getOrNull() ?: return null

        return auth.copy(password = password)
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        runCatching { passwords.delete() }
            .onFailure { Log.w(LOG_TAG, "Unable to remove the SIP password", it) }
    }

    internal companion object {
        const val KEY = "auth"
        private const val LOG_TAG = "FlutterPhoneLib"
    }
}

internal interface PasswordStore {
    fun read(): String?
    fun write(password: String)
    fun delete()
}

internal class SecurePasswordStore(context: Context) : PasswordStore {
    private val storage = SharedSecureStorage.getInstance(context)

    override fun read() = storage.read(KEY)

    override fun write(password: String) = storage.write(KEY, password)

    override fun delete() = storage.delete(KEY)

    private companion object {
        // Must stay the same as on iOS, see SipPasswordStore.swift.
        const val KEY = "flutter_phone_lib.sip_password"
    }
}
