package com.settleup.android.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "auth")

@Singleton
class TokenProvider @Inject constructor(@ApplicationContext private val context: Context) {
    private val TOKEN_KEY = stringPreferencesKey("token")
    private val USER_ID_KEY = stringPreferencesKey("userId")
    private val REFRESH_KEY = stringPreferencesKey("refreshToken")

    suspend fun getToken(): String? = context.dataStore.data.map { it[TOKEN_KEY] }.first()
    suspend fun getUserId(): String? = context.dataStore.data.map { it[USER_ID_KEY] }.first()
    suspend fun save(token: String, refreshToken: String, userId: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[REFRESH_KEY] = refreshToken
            prefs[USER_ID_KEY] = userId
        }
    }
    suspend fun clear() = context.dataStore.edit { it.clear() }
    fun observeToken() = context.dataStore.data.map { it[TOKEN_KEY] }
}
