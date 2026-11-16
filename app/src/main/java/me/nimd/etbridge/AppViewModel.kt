package me.nimd.etbridge

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.prefs.AppPreferences
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.repo.AuthRepository
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.data.repo.SyncState
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.model.AuthSession
import me.nimd.etbridge.sync.ClassReminderScheduler
import me.nimd.etbridge.widget.WidgetUpdater
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data object SignedOut : AppState
    data class SignedIn(val session: AuthSession) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionStore: SessionStore,
    private val prefsRepo: UserPreferencesRepository,
    private val repo: EtlabRepository,
    private val auth: AuthRepository,
    private val reminders: ClassReminderScheduler,
) : ViewModel(), DefaultLifecycleObserver {

    private val loaded = MutableStateFlow(false)

    val state: StateFlow<AppState> = combine(loaded, sessionStore.session) { isLoaded, session ->
        when {
            !isLoaded -> AppState.Loading
            session == null -> AppState.SignedOut
            else -> AppState.SignedIn(session)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    val prefs: StateFlow<AppPreferences?> =
        prefsRepo.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val syncState: StateFlow<SyncState> = repo.syncState

    val lastSync: StateFlow<Long?> = repo.lastSync.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var watchdog: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.load()
            val p = prefsRepo.snapshot()
            _locked.value = p.biometricLock && sessionStore.current != null
            loaded.value = true
        }
        // Refresh (stale-only) whenever a session appears: app start or right after login.
        viewModelScope.launch {
            sessionStore.session.filterNotNull().distinctUntilChangedBy { it.token }.collect {
                refresh(force = false)
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        viewModelScope.launch {
            val p = prefsRepo.snapshot()
            val away = backgroundedAt?.let { System.currentTimeMillis() - it }
            if (p.biometricLock && sessionStore.current != null && away != null &&
                away >= p.lockTimeoutMinutes * 60_000L
            ) {
                _locked.value = true
            }
            if (sessionStore.current != null && repo.isStale()) refresh(force = true)
            reminders.schedule()
        }
        // Webapp parity: re-check staleness every 15 minutes while the app is visible.
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            while (isActive) {
                delay(15 * 60_000L)
                if (sessionStore.current != null && repo.isStale()) refresh(force = true)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
        watchdog?.cancel()
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            repo.refreshAll(force)
            WidgetUpdater.updateAll(context)
        }
    }

    fun dismissSyncError() = repo.clearSyncError()

    fun unlock() {
        _locked.value = false
    }

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            reminders.cancelAll()
            _locked.value = false
            WidgetUpdater.updateAll(context)
        }
    }

    /** True when the device's preferences finished loading (used to theme the first frame). */
    suspend fun awaitPrefs(): AppPreferences = prefs.map { it }.filterNotNull().first()

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}
