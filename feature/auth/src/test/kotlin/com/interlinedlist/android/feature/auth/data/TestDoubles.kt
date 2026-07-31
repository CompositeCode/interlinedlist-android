package com.interlinedlist.android.feature.auth.data

import android.content.SharedPreferences
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.database.dao.UserDao
import com.interlinedlist.android.core.database.entity.CachedUserEntity
import com.interlinedlist.android.core.datastore.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** DispatcherProvider that runs everything on the supplied test dispatcher. */
class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val main: CoroutineDispatcher get() = dispatcher
}

/** Records what the repository writes so tests can assert cache/session effects. */
class FakeUserDao : UserDao {
    val stored = MutableStateFlow<CachedUserEntity?>(null)
    var cleared = false

    override fun observeUser(id: String): Flow<CachedUserEntity?> = stored
    override suspend fun upsert(user: CachedUserEntity) {
        stored.value = user
    }

    override suspend fun clear() {
        cleared = true
        stored.value = null
    }
}

/** Builds a [SessionStore] backed by in-memory prefs so tests need no Android runtime. */
fun fakeSessionStore(): SessionStore = SessionStore(InMemorySharedPreferences())

/** Minimal in-memory [SharedPreferences] covering the getString/putString/clear path. */
private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getString(key: String?, defValue: String?): String? =
        (values[key] as? String) ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun getAll(): MutableMap<String, *> = values
    override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (values[key] as? Boolean) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? MutableSet<String>) ?: defValues

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
            apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = REMOVED }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) values.clear()
            pending.forEach { (k, v) -> if (v === REMOVED) values.remove(k) else values[k] = v }
            pending.clear()
            clear = false
        }
    }

    companion object {
        private val REMOVED = Any()
    }
}
