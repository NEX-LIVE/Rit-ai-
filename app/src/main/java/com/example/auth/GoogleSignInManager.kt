package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "GoogleSignInManager"

sealed interface GoogleSignInResult {
    data class Success(val user: FirebaseUser) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

object GoogleSignInManager {

    suspend fun attemptSilentSignIn(context: Context): FirebaseUser? {
        Firebase.auth.currentUser?.let { return it }
        val clientId = runCatching {
            context.getString(R.string.default_web_client_id)
        }.getOrNull() ?: return null

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                authResult.user
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google Sign-In unavailable: ${e.message}")
            null
        }
    }

    suspend fun signInInteractive(context: Context): GoogleSignInResult {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return GoogleSignInResult.Error(
                "Google Sign-In configuration missing: default_web_client_id not found"
            )
        }

        val activity = context as? Activity
            ?: return GoogleSignInResult.Error("Interactive sign-in requires an Activity context.")

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                if (user != null) {
                    GoogleSignInResult.Success(user)
                } else {
                    GoogleSignInResult.Error("Sign-in returned an empty Firebase user.")
                }
            } else {
                GoogleSignInResult.Error("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            GoogleSignInResult.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInResult.Error(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            val credentialManager = CredentialManager.create(context)
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
            }
        }
    }
}
