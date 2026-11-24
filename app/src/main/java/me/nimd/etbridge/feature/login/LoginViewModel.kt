package me.nimd.etbridge.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.network.ApiException
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.repo.AuthRepository
import me.nimd.etbridge.core.model.Institute
import java.io.IOException
import javax.inject.Inject

data class LoginUiState(
    val institutes: List<Institute> = emptyList(),
    val institutesLoading: Boolean = true,
    val selected: Institute? = null,
    val username: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && username.isNotBlank() && password.isNotBlank() &&
            (selected != null || (username.trim().equals("demo", true) && password.trim().equals("demo", true)))
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val list = auth.institutes().sortedBy { it.name.lowercase() }
            val remembered = prefs.snapshot().lastSelectedInstituteId
            _state.update { s ->
                s.copy(
                    institutes = list,
                    institutesLoading = false,
                    selected = s.selected ?: list.firstOrNull { it.id == remembered },
                )
            }
        }
    }

    fun select(institute: Institute) = _state.update { it.copy(selected = institute, error = null) }

    /** Lets students type a code that isn't in the list (it's their portal's subdomain). */
    fun useCustomCode(code: String) {
        val id = code.trim().lowercase().removeSuffix(".etlab.app").substringAfterLast('/')
        if (id.isBlank()) return
        select(Institute(id = id, name = id.uppercase(), shortName = id.uppercase()))
    }

    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val inst = s.selected
                auth.login(s.username, s.password, inst?.id ?: "demo", inst?.shortName ?: inst?.name, inst?.apiBaseUrl)
                // AppViewModel observes the session and swaps to the main UI.
            } catch (e: ApiException) {
                _state.update { it.copy(submitting = false, error = e.message) }
            } catch (_: IOException) {
                _state.update { it.copy(submitting = false, error = "Couldn't reach ETLab. Check your connection and try again.") }
            } catch (e: Exception) {
                _state.update { it.copy(submitting = false, error = e.message ?: "Sign-in failed") }
            }
        }
    }

    fun demo() {
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch { auth.loginDemo() }
    }
}
