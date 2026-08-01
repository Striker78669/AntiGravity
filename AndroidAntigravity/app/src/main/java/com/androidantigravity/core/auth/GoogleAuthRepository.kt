package com.androidantigravity.core.auth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.androidantigravity.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class SignedInUser(val subject: String, val email: String, val name: String? = null, val picture: String? = null)

class GoogleAuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) { install(ContentNegotiation) { json(json) } }

    suspend fun signIn(activity: Activity): SignedInUser {
        return try {
            check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                "Google sign-in is not configured. Add GOOGLE_WEB_CLIENT_ID to local.properties."
            }
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
            val result = credentialManager.getCredential(
                context = activity,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
            val credential = result.credential as? CustomCredential
                ?: error("Google returned an unsupported credential.")
            check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Google returned an unexpected credential type."
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            client.post("${BuildConfig.API_BASE_URL}/v1/auth/google") {
                contentType(ContentType.Application.Json)
                setBody(GoogleAuthRequest(token))
            }.body()
        } catch (error: Throwable) {
            if (BuildConfig.DEBUG) throw IllegalStateException("${error.message}\n\n${installedAppIdentity()}", error)
            throw error
        }
    }

    private fun installedAppIdentity(): String = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        } else null
        @Suppress("DEPRECATION")
        val packageInfo = if (flags != null) appContext.packageManager.getPackageInfo(appContext.packageName, flags)
        else appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        val certificate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                ?: error("No signing certificate was found for the installed app.")
        } else {
            packageInfo.signatures?.firstOrNull()?.toByteArray()
                ?: error("No signing certificate was found for the installed app.")
        }
        val sha1 = MessageDigest.getInstance("SHA-1").digest(certificate).joinToString(":") { "%02X".format(it) }
        "Installed package: ${appContext.packageName}\nInstalled SHA-1: $sha1\nWeb client ID: ${BuildConfig.GOOGLE_WEB_CLIENT_ID}"
    }.getOrElse { "Could not read the installed app certificate: ${it.message}" }
}

@Serializable private data class GoogleAuthRequest(val id_token: String)
