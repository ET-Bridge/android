package me.nimd.etbridge.core.data.repo

import kotlinx.coroutines.flow.StateFlow
import me.nimd.etbridge.core.data.demo.DemoDataSource
import me.nimd.etbridge.core.data.network.EtlabEndpointConfig
import me.nimd.etbridge.core.data.network.EtlabApi
import me.nimd.etbridge.core.data.network.EtlabEndpoints
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.model.AuthSession
import me.nimd.etbridge.core.model.DEMO_TOKEN
import me.nimd.etbridge.core.model.Institute
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: EtlabApi,
    private val demo: DemoDataSource,
    private val sessionStore: SessionStore,
    private val prefs: UserPreferencesRepository,
    private val etlab: EtlabRepository,
    private val dutyLeaves: DutyLeaveRepository,
    private val endpointConfig: EtlabEndpointConfig,
) {
    val session: StateFlow<AuthSession?> = sessionStore.session

    /** ETLab's institute list, falling back to the bundled list (FALLBACK_INSTITUTES). */
    suspend fun institutes(): List<Institute> {
        val remote = runCatching { api.institutes() }.getOrNull().orEmpty()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        return remote.ifEmpty { runCatching { demo.institutes() }.getOrDefault(emptyList()) }
    }

    fun isDemoCredentials(username: String, password: String) =
        username.trim().equals("demo", ignoreCase = true) && password.trim().equals("demo", ignoreCase = true)

    suspend fun login(
        username: String,
        password: String,
        instituteId: String,
        instituteName: String?,
        instituteApiBaseUrl: String? = null,
    ): AuthSession {
        if (isDemoCredentials(username, password)) return loginDemo()
        // Make sure we talk to ETLab's current host (remote config) or the college's own one.
        endpointConfig.applyCached()
        endpointConfig.refresh()
        val apiOrigin = EtlabEndpoints.normalizeOrigin(instituteApiBaseUrl)
        EtlabEndpoints.setInstituteOrigin(apiOrigin)
        val result = try {
            api.login(username, password, instituteId)
        } catch (e: Exception) {
            EtlabEndpoints.setInstituteOrigin(sessionStore.current?.apiOrigin)
            throw e
        }
        val session = AuthSession(
            token = result.token,
            instituteId = instituteId,
            instituteName = instituteName?.takeIf { it.isNotBlank() } ?: instituteId.uppercase(),
            username = username.trim(),
            studentName = result.name ?: username.trim(),
            studentPhoto = result.photo,
            apiOrigin = apiOrigin,
        )
        // A different account may have used this device; never mix cached data.
        etlab.clear()
        sessionStore.save(session)
        prefs.setLastInstitute(instituteId)
        return session
    }

    suspend fun loginDemo(): AuthSession {
        val session = AuthSession(
            token = DEMO_TOKEN,
            instituteId = "demo",
            instituteName = "Demo Institute of Technology",
            username = "demo",
            studentName = "Demo Student",
        )
        etlab.clear()
        sessionStore.save(session)
        return session
    }

    /** Wipes the session, every cached payload and all self-reported duty leaves. */
    suspend fun logout() {
        sessionStore.clear()
        etlab.clear()
        dutyLeaves.clearAll()
        prefs.clearAccountState()
    }
}
