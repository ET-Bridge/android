package me.nimd.etbridge.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.model.EtlabJson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [EtlabEndpoints] pointed at ETLab's current API host without an app update.
 *
 * Reads a small static file published with the web app (webapp/public/etlab-endpoints.json,
 * checked against ETLab's own client by the "ETLab watch" workflow). The request carries no
 * token, username or other personal data, and only https origins on etlab.app (or a subdomain) are accepted,
 * so the file can't redirect the app anywhere else.
 */
@Singleton
class EtlabEndpointConfig @Inject constructor(
    private val client: HttpClient,
    private val prefs: UserPreferencesRepository,
) {
    companion object {
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }

    /** Applies the last known remote origin (fast, offline). Call once at start-up. */
    suspend fun applyCached() {
        EtlabEndpoints.setRemoteOrigin(prefs.remoteApiOrigin().first)
    }

    /** Re-fetches the config if it hasn't been checked recently (or [force]). Never throws. */
    suspend fun refresh(force: Boolean = false) {
        val (_, checked) = prefs.remoteApiOrigin()
        val now = System.currentTimeMillis()
        if (!force && checked != null && now - checked < CHECK_INTERVAL_MS) return
        try {
            val res = client.get(EtlabEndpoints.REMOTE_CONFIG_URL)
            if (!res.status.isSuccess()) return
            val raw = (EtlabJson.parseToJsonElement(res.bodyAsText()).jsonObject["apiOrigin"] as? JsonPrimitive)?.contentOrNull
            val origin = EtlabEndpoints.normalizeOrigin(raw)
            prefs.setRemoteApiOrigin(origin, now)
            EtlabEndpoints.setRemoteOrigin(origin)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline or the site is down: keep using the cached/default origin.
        }
    }
}
