package me.nimd.etbridge.core.data.network

import me.nimd.etbridge.core.data.session.SessionStore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

object EtlabEndpoints {
    const val API_ORIGIN = "https://api.students.etlab.app"
    const val API_BASE = "$API_ORIGIN/api/v1"
    const val WEB_ORIGIN = "https://students.etlab.app"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun isEtlabHost(host: String): Boolean = host == "etlab.app" || host.endsWith(".etlab.app")

    /** Relative ETLab paths ("/uploads/x.pdf") become absolute API URLs, as in download.ts. */
    fun absolute(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> "$API_ORIGIN$url"
        else -> "$API_ORIGIN/$url"
    }
}

/**
 * Adds the headers that the webapp's Cloudflare/Vite proxy injected (official Origin/Referer and
 * a browser User-Agent) plus the bearer token and institute id, for ETLab hosts only. Because
 * the same OkHttpClient backs Ktor, Coil and downloads, authenticated images and files work
 * without any proxy.
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
        if (session != null && !session.isDemo && request.header("Authorization") == null &&
            request.header(SKIP_AUTH) == null
        ) {
            builder.header("Authorization", "Bearer ${session.token}")
            builder.header("X-Institute-ID", session.instituteId)
        }
        builder.removeHeader(SKIP_AUTH)
        return chain.proceed(builder.build())
    }

    companion object {
        /** Marker header for requests (login, institutes) that must not carry a token. */
        const val SKIP_AUTH = "X-Etbridge-Skip-Auth"
    }
}

fun String.isEtlabUrl(): Boolean = toHttpUrlOrNull()?.host?.let(EtlabEndpoints::isEtlabHost) ?: false
