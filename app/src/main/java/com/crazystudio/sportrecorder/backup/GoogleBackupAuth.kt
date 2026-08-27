package com.crazystudio.sportrecorder.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val ABOUT_URL = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)"
private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"

/**
 * Authorizes the app for Drive's appDataFolder scope and exposes the resulting access token.
 * Token acquisition is on-demand and not persisted; the signed-in account email is read from the
 * Drive `about` endpoint.
 */
class GoogleBackupAuth(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : BackupAuth {

    private val authorizationClient = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        .build()

    private val _account = MutableStateFlow<BackupAccount?>(null)
    override val account: Flow<BackupAccount?> = _account.asStateFlow()

    /** A valid Drive appdata token, or throws [BackupAuthorizationRequiredException] if consent is needed. */
    suspend fun accessToken(): String {
        val result = authorizationClient.authorize(request).await()
        return tokenFrom(result)
    }

    /** Silent attempt to populate [account] (e.g. on screen open). Leaves it null if consent is needed. */
    suspend fun refreshAccount() {
        val result = authorizationClient.authorize(request).await()
        if (!result.hasResolution()) {
            result.accessToken?.let { updateAccount(it) }
        } else {
            _account.value = null
        }
    }

    /**
     * Complete authorization after the UI launched the consent PendingIntent. Returns whether we
     * ended up signed in — cancelling the sheet yields no token and leaves the user signed out.
     */
    suspend fun onAuthorizationResult(data: Intent?): Boolean {
        val result = authorizationClient.getAuthorizationResultFromIntent(data)
        val token = result.accessToken
        if (token == null) {
            _account.value = null
        } else {
            updateAccount(token)
        }
        return _account.value != null
    }

    /**
     * Revoke the Drive grant, then forget the account — so signing in again really asks which
     * account to use. Revocation needs the network, and throwing when it fails is deliberate: a
     * grant we couldn't revoke would silently sign the user back in on the next launch, so we
     * leave them signed in and let the UI say so rather than showing a sign-out that didn't hold.
     */
    suspend fun signOut() {
        val token = authorizationClient.authorize(request).await().accessToken
        if (token != null) revokeToken(token)
        _account.value = null
    }

    private suspend fun tokenFrom(result: AuthorizationResult): String {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: error("Authorization needs resolution but no PendingIntent was provided")
            throw BackupAuthorizationRequiredException(pendingIntent)
        }
        val token = result.accessToken ?: error("Authorization returned no access token")
        updateAccount(token)
        return token
    }

    private suspend fun revokeToken(token: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$REVOKE_URL?token=$token")
            .post("".toRequestBody())
            .build()
        httpClient.newCall(req).execute().use { response ->
            check(response.isSuccessful) { "Revoking the Drive grant failed (HTTP ${response.code})" }
        }
    }

    private suspend fun updateAccount(token: String) {
        val email = fetchEmail(token)
        if (email != null) _account.value = BackupAccount(email)
    }

    private suspend fun fetchEmail(token: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(ABOUT_URL)
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            JSONObject(body).optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotEmpty() }
        }
    }
}
