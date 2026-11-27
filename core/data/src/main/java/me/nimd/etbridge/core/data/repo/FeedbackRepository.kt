package me.nimd.etbridge.core.data.repo

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.model.EtlabJson
import javax.inject.Inject
import javax.inject.Singleton

enum class FeedbackCategory(val id: String, val label: String) {
    MissingFeature("missing_feature", "Missing feature"),
    DataDiscrepancy("data_discrepancy", "Attendance / marks error"),
    Bug("bug_glitch", "Bug / broken UI"),
    Other("other", "Other feedback"),
}

/** Same Web3Forms endpoint and access key as the webapp's FeedbackModal. */
@Singleton
class FeedbackRepository @Inject constructor(
    private val client: HttpClient,
    private val sessionStore: SessionStore,
) {
    private companion object {
        const val ENDPOINT = "https://api.web3forms.com/submit"
        const val ACCESS_KEY = "68f7effe-e08f-4fe2-8342-a32019d2db20"
    }

    /**
     * Sends feedback. Web3Forms delivers the email but often replies with an HTML page (or a redirect to one)
     * instead of JSON, so a non-JSON 2xx/3xx reply counts as sent unless it is Web3Forms' failure page.
     */
    suspend fun submit(
        category: FeedbackCategory,
        name: String,
        email: String,
        message: String,
        diagnostics: Map<String, String> = emptyMap(),
    ): Result<Unit> = runCatching {
        val session = sessionStore.current
        val footer = if (diagnostics.isEmpty()) "" else
            "\n\n---\n" + diagnostics.entries.joinToString("\n") { (k, v) -> "$k: $v" }
        val res = client.submitForm(
            url = ENDPOINT,
            formParameters = parameters {
                append("access_key", ACCESS_KEY)
                append("category", category.id)
                append("name", name)
                append("email", email)
                append("message", message + footer)
                session?.instituteId?.let { append("institute", it) }
                session?.username?.let { append("username", it) }
                diagnostics.forEach { (k, v) -> append(k.lowercase().replace(' ', '_'), v) }
                append("from_name", "ETBridge Android Feedback")
                append("subject", "[ETBridge Android] ${category.id.uppercase()} - ${session?.instituteId ?: "User"}")
            },
        ) { header(HttpHeaders.Accept, "application/json") }
        val body = runCatching { res.bodyAsText() }.getOrDefault("")
        val json = runCatching { EtlabJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val code = res.status.value
        when {
            json != null -> {
                val success = (json["success"] as? JsonPrimitive)?.booleanOrNull == true
                if (!success) {
                    error((json["message"] as? JsonPrimitive)?.contentOrNull ?: "Couldn't send feedback (HTTP $code). Please try again.")
                }
            }
            code >= 400 || body.contains("submission failed", ignoreCase = true) ->
                error("Couldn't send feedback (HTTP $code). Please try again.")
            else -> Unit // HTML success page: the email went out.
        }
    }
}
