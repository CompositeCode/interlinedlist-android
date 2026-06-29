package com.interlinedlist.android.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.interlinedlist.android.core.common.session.SessionTokenProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the authenticated session (bearer token + user id) in
 * EncryptedSharedPreferences, so the `il_tok_` value is encrypted at rest.
 * Implements [SessionTokenProvider] for the network layer's auth interceptor.
 */
@Singleton
class SessionStore @Inject constructor(
    private val prefs: SharedPreferences,
) : SessionTokenProvider {

    override fun currentToken(): String? = prefs.getString(KEY_TOKEN, null)

    val isLoggedIn: Boolean
        get() = !currentToken().isNullOrBlank()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_USER_ID, value).apply()
        }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /** Clears all session state (used on logout / 401). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "bearer_token"
        private const val KEY_USER_ID = "user_id"
        private const val PREFS_FILE = "il_session.prefs"

        /** Builds the AES-256 encrypted preferences backing the session. */
        fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
