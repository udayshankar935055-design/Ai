package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ImageAspectRatio
import com.example.data.ai.ImageQualityTier
import com.example.data.ai.SmartAiService
import com.example.network.ConnectivityObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ImageStudioViewModel"

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data object Loading : ImageStudioUiState
    data class Success(
        val bitmap: Bitmap,
        val prompt: String,
        val aspectRatio: ImageAspectRatio
    ) : ImageStudioUiState
    data class Error(val message: String) : ImageStudioUiState
}

class ImageStudioViewModel(
    private val aiService: SmartAiService,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt.asStateFlow()

    private val _aspectRatio = MutableStateFlow(ImageAspectRatio.RATIO_1_1)
    val aspectRatio: StateFlow<ImageAspectRatio> = _aspectRatio.asStateFlow()

    private val _qualityTier = MutableStateFlow(ImageQualityTier.STANDARD)
    val qualityTier: StateFlow<ImageQualityTier> = _qualityTier.asStateFlow()

    private val _sourceImage = MutableStateFlow<Bitmap?>(null)
    val sourceImage: StateFlow<Bitmap?> = _sourceImage.asStateFlow()

    private val _uiState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    fun setPrompt(value: String) {
        _prompt.value = value
    }

    fun setAspectRatio(ratio: ImageAspectRatio) {
        _aspectRatio.value = ratio
    }

    fun setQualityTier(tier: ImageQualityTier) {
        _qualityTier.value = tier
    }

    fun setSourceImage(bitmap: Bitmap?) {
        _sourceImage.value = bitmap
    }

    fun generateImage() {
        val currentPrompt = _prompt.value.trim()
        if (currentPrompt.isBlank()) {
            _uiState.value = ImageStudioUiState.Error("Please enter an image prompt or editing instruction.")
            return
        }
        if (!isOnline.value) {
            _uiState.value = ImageStudioUiState.Error("Internet connection is required to generate images.")
            return
        }

        val ratio = _aspectRatio.value
        val tier = _qualityTier.value
        val inputBmp = _sourceImage.value

        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading
            try {
                val result = aiService.generateOrEditImage(
                    prompt = currentPrompt,
                    aspectRatio = ratio,
                    qualityTier = tier,
                    sourceBitmap = inputBmp
                )
                if (result != null) {
                    _uiState.value = ImageStudioUiState.Success(result, currentPrompt, ratio)
                } else {
                    _uiState.value = ImageStudioUiState.Error("No image was returned by the model. Try refining your prompt.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = ImageStudioUiState.Error("Image generation timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageStudioUiState.Error(
                    e.localizedMessage ?: "Image generation failed. Please check billing/quota and try again."
                )
            }
        }
    }
}
