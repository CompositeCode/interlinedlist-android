package com.interlinedlist.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.database.dao.UserDao
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    sessionStore: SessionStore,
    userDao: UserDao,
) : ViewModel() {

    /** Display name (or username) of the cached signed-in user, if any. */
    val displayName: StateFlow<String?> =
        sessionStore.userId?.let { id ->
            userDao.observeUser(id)
                .map { it?.displayName ?: it?.username }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        } ?: MutableStateFlow<String?>(null)

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
