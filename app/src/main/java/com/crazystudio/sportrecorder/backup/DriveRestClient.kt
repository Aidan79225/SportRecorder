package com.crazystudio.sportrecorder.backup

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DEFAULT_API_BASE_URL = "https://www.googleapis.com"
private const val FILES_PATH = "/drive/v3/files"
private const val UPLOAD_PATH = "/upload/drive/v3/files"
private const val PAGE_SIZE = "1000"
private const val LIST_FIELDS = "nextPageToken,files(id,name,size,appProperties)"
private const val AUTH_HEADER = "Authorization"
private const val MIME_JSON_METADATA = "application/json; charset=UTF-8"
private const val TAG = "DriveRestClient"

/** Drive's JSON error body says *why* (accessNotConfigured, insufficientPermissions, quota…). */
private const val ERROR_BODY_LIMIT = 400

private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val IO_TIMEOUT_SECONDS = 120L

/**
 * Thin, suspendable wrapper over the Drive v3 REST endpoints the backup store needs. Knows nothing
 * about snapshots. [apiBaseUrl] is injectable so tests can point it at a MockWebServer; JSON is
 * parsed with kotlinx.serialization (not org.json) so the class runs in plain JVM unit tests.
 *
 * Every call uses OkHttp's async `enqueue` and cancels the HTTP call when the coroutine is
 * cancelled, so a user's "取消" stops the transfer promptly instead of after the current file.
 */
class DriveRestClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
) {
    data class DriveFile(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val appProperties: Map<String, String>,
    )

    /** Every file in appDataFolder, across all pages. */
    suspend fun listAppDataFiles(token: String): List<DriveFile> {
        val all = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "$apiBaseUrl$FILES_PATH".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("pageSize", PAGE_SIZE)
                .addQueryParameter("fields", LIST_FIELDS)
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
            val body = execute(Request.Builder().url(url).header(AUTH_HEADER, "Bearer $token").build(), "Drive list")
                .use { it.body?.string() ?: throw IOException("Drive list returned an empty body") }
            val json = Json.parseToJsonElement(body).jsonObject
            json["files"]?.jsonArray?.forEach { element ->
                val obj = element.jsonObject
                all += DriveFile(
                    id = obj["id"]?.jsonPrimitive?.content ?: throw IOException("Drive file without id"),
                    name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    sizeBytes = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    appProperties = obj["appProperties"]?.jsonObject
                        ?.mapValues { (_, value) -> value.jsonPrimitive.content }
                        ?: emptyMap(),
                )
            }
            pageToken = json["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (pageToken != null)
        return all
    }

    suspend fun uploadMultipart(
        token: String,
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
        bytes: ByteArray,
    ) {
        val metadata = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add("appDataFolder") }
            put("mimeType", mimeType)
            putJsonObject("appProperties") { appProperties.forEach { (key, value) -> put(key, value) } }
        }.toString()
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(MIME_JSON_METADATA.toMediaType()))
            .addPart(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val url = "$apiBaseUrl$UPLOAD_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .build()
        val request = Request.Builder().url(url).header(AUTH_HEADER, "Bearer $token").post(body).build()
        execute(request, "Drive upload of $name").close()
    }

    suspend fun downloadBytes(token: String, fileId: String): ByteArray {
        val url = "$apiBaseUrl$FILES_PATH/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder().url(url).header(AUTH_HEADER, "Bearer $token").build()
        return execute(request, "Drive download").use {
            it.body?.bytes() ?: throw IOException("Drive download returned an empty body")
        }
    }

    suspend fun deleteFile(token: String, fileId: String) {
        val request = Request.Builder()
            .url("$apiBaseUrl$FILES_PATH/$fileId")
            .header(AUTH_HEADER, "Bearer $token")
            .delete()
            .build()
        execute(request, "Drive delete").close()
    }

    /** Run [request]; a non-2xx response is turned into an [IOException] carrying Drive's reason. */
    private suspend fun execute(request: Request, what: String): Response {
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) response.fail(what)
        return response
    }
}

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    // OkHttp's 10-second defaults are too tight: a backup uploads every photo as its own request
    // over whatever connection the phone has, so an upload that is merely slow can outlive them.
    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()

/** Suspend on an OkHttp call; cancelling the coroutine cancels the HTTP call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        },
    )
    continuation.invokeOnCancellation { cancel() }
}

/**
 * Fail a Drive call with the reason Drive actually gave. The HTTP code alone does not say whether
 * the Drive API is disabled for the project, the grant is missing the appdata scope, or the account
 * is out of storage — the JSON error body does, so carry it into the message and the log.
 */
private fun Response.fail(what: String): Nothing {
    val detail = runCatching { body?.string().orEmpty() }
        .getOrDefault("")
        .replace('\n', ' ')
        .trim()
        .take(ERROR_BODY_LIMIT)
    close()
    Log.w(TAG, "$what failed: HTTP $code ${detail.ifBlank { "(no body)" }}")
    throw IOException("$what failed: HTTP $code${if (detail.isBlank()) "" else " — $detail"}")
}
