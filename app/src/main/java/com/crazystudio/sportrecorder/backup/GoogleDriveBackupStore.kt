package com.crazystudio.sportrecorder.backup

import android.content.Context
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"

// Single-page listing (no nextPageToken handling). Fine for keep-last-3 with modest photo
// counts; if appDataFolder ever exceeds 1000 files a truncated listing could make prune treat
// still-referenced photos as orphans. Add paging before users accumulate large libraries.
private const val DRIVE_LIST_URL =
    "https://www.googleapis.com/drive/v3/files" +
        "?spaces=appDataFolder&pageSize=1000&fields=files(id,name,size,appProperties)"
private const val DRIVE_UPLOAD_URL =
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

private const val AUTH_HEADER = "Authorization"
private const val MIME_JSON = "application/json"
private const val MIME_JSON_METADATA = "application/json; charset=UTF-8"
private const val MIME_WEBP = "image/webp"

private const val KIND = "kind"
private const val KIND_MANIFEST = "manifest"
private const val KIND_PHOTO = "photo"
private const val KEY_SNAPSHOT_ID = "snapshotId"
private const val KEY_CREATED_AT = "createdAt"
private const val KEY_APP_VERSION = "appVersionName"

/**
 * Android [BackupStore] backed by Google Drive's `appDataFolder` over the Drive v3 REST API.
 *
 * Storage model: every manifest and photo is a flat file in `appDataFolder`, tagged through Drive
 * `appProperties`. A manifest carries `kind=manifest` plus the snapshot's id/createdAt/appVersionName;
 * a photo carries `kind=photo` and is deduped by file name (photos are shared across snapshots). The
 * manifest is always uploaded last so it acts as the snapshot's commit marker.
 */
class GoogleDriveBackupStore(
    private val auth: GoogleBackupAuth,
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : BackupStore {

    private data class DriveFile(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val appProperties: Map<String, String>,
    )

    override suspend fun listSnapshots(): List<SnapshotInfo> = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        listFiles(token)
            .filter { it.appProperties[KIND] == KIND_MANIFEST }
            .map { file ->
                SnapshotInfo(
                    id = file.appProperties[KEY_SNAPSHOT_ID].orEmpty(),
                    createdAt = file.appProperties[KEY_CREATED_AT]?.toLongOrNull() ?: 0L,
                    appVersionName = file.appProperties[KEY_APP_VERSION].orEmpty(),
                    sizeBytes = file.sizeBytes,
                )
            }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun uploadSnapshot(
        manifestJson: String,
        photoFileNames: List<String>,
    ): SnapshotInfo = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        // Parse first so a malformed manifest fails before any photo is uploaded (no orphans).
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), manifestJson)
        val existingPhotos = listFiles(token)
            .filter { it.appProperties[KIND] == KIND_PHOTO }
            .map { it.name }
            .toSet()
        photoFileNames
            .filterNot { it in existingPhotos }
            .forEach { name ->
                val bytes = PhotoStorage.fileFor(context, name).readBytes()
                uploadMultipart(token, name, MIME_WEBP, mapOf(KIND to KIND_PHOTO), bytes)
            }
        val snapshotId = UUID.randomUUID().toString()
        val manifestBytes = manifestJson.encodeToByteArray()
        val props = mapOf(
            KIND to KIND_MANIFEST,
            KEY_SNAPSHOT_ID to snapshotId,
            KEY_CREATED_AT to doc.createdAt.toString(),
            KEY_APP_VERSION to doc.appVersionName,
        )
        uploadMultipart(token, "manifest-$snapshotId.json", MIME_JSON, props, manifestBytes)
        SnapshotInfo(snapshotId, doc.createdAt, doc.appVersionName, manifestBytes.size.toLong())
    }

    override suspend fun downloadManifest(id: String): String = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val manifest = findManifest(listFiles(token), id)
        downloadBytes(token, manifest.id).decodeToString()
    }

    override suspend fun downloadPhotos(id: String) = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val files = listFiles(token)
        val manifest = findManifest(files, id)
        val doc = BackupJson.decodeFromString(
            BackupDocument.serializer(),
            downloadBytes(token, manifest.id).decodeToString(),
        )
        val referenced = doc.meals.flatMap { it.photos }.map { it.fileName }.distinct()
        val photoIdByName = files
            .filter { it.appProperties[KIND] == KIND_PHOTO }
            .associate { it.name to it.id }
        referenced.forEach { name ->
            // Guard: names come from a downloaded manifest; never let one escape the photos dir.
            require(!name.contains('/') && !name.contains('\\')) {
                "Snapshot $id has an illegal photo file name: $name"
            }
            val fileId = photoIdByName[name]
                ?: throw IOException("Snapshot $id references a photo missing from Drive: $name")
            PhotoStorage.fileFor(context, name).writeBytes(downloadBytes(token, fileId))
        }
    }

    override suspend fun prune(keepLast: Int) = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val files = listFiles(token)
        val manifests = files
            .filter { it.appProperties[KIND] == KIND_MANIFEST }
            .sortedByDescending { it.appProperties[KEY_CREATED_AT]?.toLongOrNull() ?: 0L }
        manifests.drop(keepLast).forEach { deleteFile(token, it.id) }
        val keptPhotoNames = manifests.take(keepLast).flatMap { manifest ->
            val doc = BackupJson.decodeFromString(
                BackupDocument.serializer(),
                downloadBytes(token, manifest.id).decodeToString(),
            )
            doc.meals.flatMap { meal -> meal.photos.map { it.fileName } }
        }.toSet()
        files
            .filter { it.appProperties[KIND] == KIND_PHOTO && it.name !in keptPhotoNames }
            .forEach { deleteFile(token, it.id) }
    }

    private fun findManifest(files: List<DriveFile>, id: String): DriveFile =
        files.firstOrNull {
            it.appProperties[KIND] == KIND_MANIFEST && it.appProperties[KEY_SNAPSHOT_ID] == id
        } ?: throw IOException("No manifest found in Drive for snapshot $id")

    private fun listFiles(token: String): List<DriveFile> {
        val request = Request.Builder()
            .url(DRIVE_LIST_URL)
            .header(AUTH_HEADER, "Bearer $token")
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Drive list failed: HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Drive list returned an empty body")
        }
        val filesArray = JSONObject(body).optJSONArray("files") ?: JSONArray()
        return (0 until filesArray.length()).map { index ->
            val obj = filesArray.getJSONObject(index)
            val propsObj = obj.optJSONObject("appProperties")
            val props = buildMap {
                if (propsObj != null) {
                    propsObj.keys().forEach { key -> put(key, propsObj.getString(key)) }
                }
            }
            DriveFile(
                id = obj.getString("id"),
                name = obj.optString("name"),
                sizeBytes = obj.optString("size").toLongOrNull() ?: 0L,
                appProperties = props,
            )
        }
    }

    private fun uploadMultipart(
        token: String,
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
        bytes: ByteArray,
    ) {
        val propsJson = JSONObject()
        appProperties.forEach { (key, value) -> propsJson.put(key, value) }
        val metadata: JSONObject = JSONObject()
            .put("name", name)
            .put("parents", JSONArray(listOf("appDataFolder")))
            .put("mimeType", mimeType)
            .put("appProperties", propsJson)
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(MIME_JSON_METADATA.toMediaType()))
            .addPart(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val request = Request.Builder()
            .url(DRIVE_UPLOAD_URL)
            .header(AUTH_HEADER, "Bearer $token")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Drive upload of $name failed: HTTP ${response.code}")
        }
    }

    private fun downloadBytes(token: String, fileId: String): ByteArray {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .header(AUTH_HEADER, "Bearer $token")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Drive download failed: HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("Drive download returned an empty body")
        }
    }

    private fun deleteFile(token: String, fileId: String) {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId")
            .header(AUTH_HEADER, "Bearer $token")
            .delete()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Drive delete failed: HTTP ${response.code}")
        }
    }
}
