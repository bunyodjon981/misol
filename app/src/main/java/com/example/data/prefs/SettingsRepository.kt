package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.TokenCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gitapk_settings")

data class UserSettings(
    val githubUsername: String = "",
    val githubToken: String = "",
    val lastRepoName: String = "",
    val isPrivateRepo: Boolean = true,
    val notificationsEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val ENCRYPTED_TOKEN = stringPreferencesKey("encrypted_github_token")
        val LAST_REPO_NAME = stringPreferencesKey("last_repo_name")
        val IS_PRIVATE_REPO = booleanPreferencesKey("is_private_repo")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            githubUsername = prefs[Keys.GITHUB_USERNAME].orEmpty(),
            githubToken = TokenCryptoManager.decrypt(prefs[Keys.ENCRYPTED_TOKEN].orEmpty()),
            lastRepoName = prefs[Keys.LAST_REPO_NAME].orEmpty(),
            isPrivateRepo = prefs[Keys.IS_PRIVATE_REPO] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
    }

    suspend fun isNotificationsEnabled(): Boolean = notificationsEnabledFlow.first()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun saveCredentials(
        username: String,
        token: String,
        repoName: String,
        isPrivate: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GITHUB_USERNAME] = username.trim()
            prefs[Keys.ENCRYPTED_TOKEN] = TokenCryptoManager.encrypt(token.trim())
            prefs[Keys.LAST_REPO_NAME] = repoName.trim()
            prefs[Keys.IS_PRIVATE_REPO] = isPrivate
        }
    }
}
