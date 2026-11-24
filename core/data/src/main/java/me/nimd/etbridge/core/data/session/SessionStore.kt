package me.nimd.etbridge.core.data.session

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.nimd.etbridge.core.model.AuthSession
import me.nimd.etbridge.core.model.EtlabJson
import me.nimd.etbridge.core.data.network.EtlabEndpoints
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "etbridge_session")
private val SESSION_KEY = stringPreferencesKey("session_v1")
private val ASSOCIATED_DATA = "etbridge-session".toByteArray()

/**
 * Holds the signed-in ETLab session. The serialized session (including the bearer token) is
 * encrypted with an AES-256-GCM key that is itself wrapped by the Android Keystore, so the
 * token never touches disk in plain text.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "etbridge_keyset", "etbridge_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://etbridge_master_key")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private val persistMutex = Mutex()
    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    /** Synchronous accessor used by the OkHttp interceptor. */
    val current: AuthSession? get() = _session.value

    @Volatile private var loaded = false

    /**
     * Loads the persisted session. Called once during app start-up (splash screen keeps
     * showing until it completes) so the first frame already knows whether we're signed in.
     */
    suspend fun load(): AuthSession? = withContext(Dispatchers.IO) {
        if (loaded) return@withContext _session.value
        val stored = context.sessionDataStore.data.first()[SESSION_KEY]
        val session = stored?.let { decrypt(it) }
        EtlabEndpoints.setInstituteOrigin(session?.apiOrigin)
        _session.value = session
        loaded = true
        session
    }

    fun loadBlocking(): AuthSession? = if (loaded) _session.value else runBlocking { load() }

    suspend fun save(session: AuthSession) = withContext(Dispatchers.IO) {
        persistMutex.withLock {
            persist(session)
            EtlabEndpoints.setInstituteOrigin(session.apiOrigin)
            _session.value = session
        }
        loaded = true
    }

    private suspend fun persist(session: AuthSession) {
        val plain = EtlabJson.encodeToString(AuthSession.serializer(), session).toByteArray()
        val cipher = Base64.encodeToString(aead.encrypt(plain, ASSOCIATED_DATA), Base64.NO_WRAP)
        context.sessionDataStore.edit { it[SESSION_KEY] = cipher }
    }

    /**
     * Adopts a fresher token that ETLab sent in `X-Session-Token` (called from the OkHttp
     * interceptor). The in-memory session updates immediately so the next request uses it;
     * the encrypted copy is written in the background. Ignored if the session changed meanwhile.
     */
    fun rotateToken(expected: String, newToken: String) {
        var updated: AuthSession? = null
        _session.update { current ->
            if (current == null || current.token != expected) current
            else current.copy(token = newToken).also { updated = it }
        }
        updated?.let { s ->
            scope.launch {
                persistMutex.withLock {
                    // Skip if the user signed out or the token rotated again in the meantime.
                    if (_session.value == s) runCatching { persist(s) }
                }
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        persistMutex.withLock {
            _session.value = null
            context.sessionDataStore.edit { it.remove(SESSION_KEY) }
        }
        EtlabEndpoints.setInstituteOrigin(null)
    }

    private fun decrypt(cipher: String): AuthSession? = runCatching {
        val plain = aead.decrypt(Base64.decode(cipher, Base64.NO_WRAP), ASSOCIATED_DATA)
        EtlabJson.decodeFromString(AuthSession.serializer(), plain.decodeToString())
    }.getOrNull()
}
