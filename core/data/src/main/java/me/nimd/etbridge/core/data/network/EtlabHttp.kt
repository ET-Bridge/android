package me.nimd.etbridge.core.data.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.model.EtlabJson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Where ETLab's API lives. ETLab moved from api.students.etlab.app (Render, suspended Nov 2026)
 * to api2.students.etlab.app. The origin actually used is resolved at runtime, highest first:
 *  1. the institute's own `apiBaseUrl` (ETLab's per-college override; empty for all today),
 *  2. ETBridge's remote endpoint config (https://etbridge.nimd.me/etlab-endpoints.json), so a
 *     future move can be followed without shipping a new APK,
 *  3. [DEFAULT_API_ORIGIN].
 * Mirrors webapp/src/api/endpoints.ts.
 */
object EtlabEndpoints {
    const val DEFAULT_API_ORIGIN = "https://api2.students.etlab.app"
    val LEGACY_API_ORIGINS = setOf("https://api.students.etlab.app", "https://studentsappgo.onrender.com")
    const val WEB_ORIGIN = "https://students.etlab.app"
    const val REMOTE_CONFIG_URL = "https://etbridge.nimd.me/etlab-endpoints.json"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile private var remoteOrigin: String? = null
    @Volatile private var instituteOrigin: String? = null

    val apiOrigin: String get() = instituteOrigin ?: remoteOrigin ?: DEFAULT_API_ORIGIN
    val apiBase: String get() = "$apiOrigin/api/v1"

    fun isEtlabHost(host: String): Boolean = host == "etlab.app" || host.endsWith(".etlab.app")

    /** Validates an origin the way ETLab's client does: https, *.etlab.app, not a retired host. */
    fun normalizeOrigin(raw: String?): String? {
        val url = raw?.trim()?.takeIf { it.isNotEmpty() }?.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || !isEtlabHost(url.host)) return null
        val origin = "https://${url.host}" + if (url.port != 443) ":${url.port}" else ""
        return origin.takeIf { it !in LEGACY_API_ORIGINS }
    }

    fun setInstituteOrigin(raw: String?) { instituteOrigin = normalizeOrigin(raw) }
    fun setRemoteOrigin(raw: String?) { remoteOrigin = normalizeOrigin(raw) }

    /** Relative ETLab paths ("/uploads/x.pdf") become absolute API URLs, as in download.ts. */
    fun absolute(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> "$apiOrigin$url"
        else -> "$apiOrigin/$url"
    }
}

/**
 * ETLab tokens are JWTs valid for 3 days; responses carry a fresher one in `X-Session-Token`.
 * Accept it only for the same student, newer and unexpired — the checks ETLab's client makes.
 */
object TokenRotation {
    private data class Claims(val id: Long, val issued: Long, val expires: Long)

    private fun claims(token: String): Claims? = runCatching {
        val part = token.split('.').getOrNull(1) ?: return null
        val json = java.util.Base64.getUrlDecoder().decode(part.trimEnd('=')).decodeToString()
        val obj = EtlabJson.parseToJsonElement(json).jsonObject
        fun JsonObject.long(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
        Claims(obj.long("id") ?: return null, obj.long("iat") ?: return null, obj.long("exp") ?: return null)
    }.getOrNull()

    fun shouldAccept(candidate: String, current: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (candidate.isBlank() || current.isBlank() || candidate == current) return false
        val next = claims(candidate) ?: return false
        if (next.expires * 1000 <= nowMillis) return false
        val prev = claims(current) ?: return true
        return next.id == prev.id && next.issued > prev.issued
    }
}

/**
 * Adds the headers that the webapp's Cloudflare/Vite proxy injected (official Origin/Referer and
 * a browser User-Agent) plus the bearer token and institute id, for ETLab hosts only. Because
 * the same OkHttpClient backs Ktor, Coil and downloads, authenticated images and files work
 * without any proxy. Also adopts rotated session tokens from responses.
 */
class EtlabHeadersInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!EtlabEndpoints.isEtlabHost(request.url.host)) return chain.proceed(request)

        val builder = request.newBuilder()
            .header("Origin", EtlabEndpoints.WEB_ORIGIN)
            .header("Referer", "${EtlabEndpoints.WEB_ORIGIN}/")
            .header("User-Agent", EtlabEndpoints.USER_AGENT)
        if (request.header("Accept") == null) builder.header("Accept", "application/json")

        val session = sessionStore.current
        var sentToken: String? = null
        if (session != null && !session.isDemo && request.header("Authorization") == null &&
            request.header(SKIP_AUTH) == null
        ) {
            builder.header("Authorization", "Bearer ${session.token}")
            builder.header("X-Institute-ID", session.instituteId)
            sentToken = session.token
        }
        builder.removeHeader(SKIP_AUTH)
        val response = chain.proceed(builder.build())

        val rotated = response.header("X-Session-Token")?.trim()
        if (sentToken != null && !rotated.isNullOrEmpty() && TokenRotation.shouldAccept(rotated, sentToken)) {
            sessionStore.rotateToken(expected = sentToken, newToken = rotated)
        }
        return response
    }

    companion object {
        /** Marker header for requests (login, institutes) that must not carry a token. */
        const val SKIP_AUTH = "X-Etbridge-Skip-Auth"
    }
}

fun String.isEtlabUrl(): Boolean = toHttpUrlOrNull()?.host?.let(EtlabEndpoints::isEtlabHost) ?: false
