package com.marrosublimacion.photosms.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    companion object {
        val PHOTO_NUMBER = intPreferencesKey("photo_number")
    }

    val photoNumber: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PHOTO_NUMBER] ?: 0
        }

    suspend fun setPhotoNumber(photoNumber: Int) {
        context.dataStore.edit { preferences ->
            preferences[PHOTO_NUMBER] = photoNumber
        }
    }
}