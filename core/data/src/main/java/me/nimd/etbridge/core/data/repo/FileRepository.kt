package me.nimd.etbridge.core.data.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.nimd.etbridge.core.data.network.ApiException
import me.nimd.etbridge.core.data.network.EtlabApi
import me.nimd.etbridge.core.data.network.EtlabEndpoints
import me.nimd.etbridge.core.data.session.SessionStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadedFile(val uri: Uri, val mimeType: String, val displayName: String)

/** Authenticated downloads (port of utils/download.ts) and assignment uploads. */
@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
    private val api: EtlabApi,
    private val sessionStore: SessionStore,
) {
    companion object {
        const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
        private const val SUBFOLDER = "ETBridge"
    }

    /**
     * Downloads [url] with the student's credentials into Downloads/ETBridge (Android 10+) or the
     * app's external files dir (older), and returns a content Uri that can be opened or shared.
     */
    suspend fun download(url: String, suggestedName: String? = null): DownloadedFile = withContext(Dispatchers.IO) {
        require(url.isNotBlank() && url != "#") { "No valid download URL provided" }

        if (sessionStore.current?.isDemo == true) {
            delay(300)
            val name = sanitize(suggestedName ?: "document.pdf").ensureExtension("pdf")
            val bytes = "%PDF-1.4 Demo Document: $name\nGenerated for offline / demo mode.".toByteArray()
            return@withContext save(name, "application/pdf") { it.write(bytes) }
        }

        val fullUrl = EtlabEndpoints.absolute(url)
        val request = Request.Builder().url(fullUrl)
            .header("Accept", "application/pdf, application/octet-stream, */*")
            .build()
        okHttp.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(res.code, "Download failed (${res.code})")
            val body = res.body
            val mime = body.contentType()?.let { "${it.type}/${it.subtype}" }
                ?: guessMime(fullUrl) ?: "application/octet-stream"
            val headerName = res.header("Content-Disposition")
                ?.let { Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?").find(it)?.groupValues?.get(1) }
            val urlName = fullUrl.substringBefore('?').substringAfterLast('/').takeIf { it.contains('.') }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            val name = sanitize(suggestedName ?: headerName ?: urlName ?: "etlab-file").let { n ->
                if (ext != null && !n.contains('.')) "$n.$ext" else n
            }
            save(name, mime) { out -> body.byteStream().use { it.copyTo(out) } }
        }
    }

    private fun save(name: String, mime: String, write: (OutputStream) -> Unit): DownloadedFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Couldn't create the download file")
            try {
                resolver.openOutputStream(uri)?.use(write) ?: throw IOException("Couldn't write the download file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
            return DownloadedFile(uri, mime, name)
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), SUBFOLDER).apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use(write)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return DownloadedFile(uri, mime, name)
    }

    data class PickedFile(val name: String, val mimeType: String, val size: Long?)

    fun describe(uri: Uri): PickedFile {
        val resolver = context.contentResolver
        var name = "upload"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = resolver.getType(uri) ?: guessMime(name) ?: "application/octet-stream"
        return PickedFile(name, mime, size)
    }

    suspend fun uploadAssignment(assignmentId: String, uri: Uri) = withContext(Dispatchers.IO) {
        if (sessionStore.current?.isDemo == true) {
            delay(600)
            return@withContext
        }
        val info = describe(uri)
        if ((info.size ?: 0) > MAX_UPLOAD_BYTES) throw IOException("File is larger than 20 MB")
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Couldn't read the selected file")
        api.uploadAssignment(assignmentId, info.name, info.mimeType, bytes)
    }

    suspend fun deleteAssignment(assignmentId: String) = withContext(Dispatchers.IO) {
        if (sessionStore.current?.isDemo == true) {
            delay(400)
            return@withContext
        }
        api.deleteAssignment(assignmentId)
    }

    private fun guessMime(nameOrUrl: String): String? {
        val ext = nameOrUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "file" }

    private fun String.ensureExtension(ext: String) = if (contains('.')) this else "$this.$ext"
}
