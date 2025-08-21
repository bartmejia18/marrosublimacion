package com.marrosublimacion.photosms

import androidx.camera.core.ImageCapture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class CameraViewModel(private val state: SavedStateHandle) : ViewModel() {
    companion object { private const val KEY_FLASH = "flashMode" }
    var flashMode by mutableIntStateOf(state[KEY_FLASH] ?: ImageCapture.FLASH_MODE_AUTO)
        private set

    fun setFlash(mode: Int) {
        flashMode = mode
        state[KEY_FLASH] = mode
    }
}