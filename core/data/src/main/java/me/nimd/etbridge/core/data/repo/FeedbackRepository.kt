package me.nimd.etbridge.core.data.repo

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
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

    suspend fun submit(category: FeedbackCategory, name: String, email: String, message: String): Result<Unit> = runCatching {
        val session = sessionStore.current
        val res = client.submitForm(
            url = ENDPOINT,
            formParameters = parameters {
                append("access_key", ACCESS_KEY)
                append("category", category.id)
                append("name", name)
                append("email", email)
                append("message", message)
                session?.instituteId?.let { append("institute", it) }
                session?.username?.let { append("username", it) }
                append("from_name", "ETBridge Android Feedback")
                append("subject", "[ETBridge Android] ${category.id.uppercase()} - ${session?.instituteId ?: "User"}")
            },
        )
        val json = runCatching { EtlabJson.parseToJsonElement(res.bodyAsText()) as? JsonObject }.getOrNull()
        val success = (json?.get("success") as? JsonPrimitive)?.booleanOrNull == true
        if (!success) {
            error((json?.get("message") as? JsonPrimitive)?.contentOrNull ?: "Error submitting feedback. Please try again.")
        }
    }
}
