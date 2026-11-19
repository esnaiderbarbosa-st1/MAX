package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.services.bio.BioimpedanceAnalyzerFactory
import com.example.services.bio.BioimpedanceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BioimpedanceViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<BioimpedanceState>(BioimpedanceState.Idle)
    val uiState: StateFlow<BioimpedanceState> = _uiState.asStateFlow()

    fun executeAnalysis(imageBytes: ByteArray) {
        viewModelScope.launch {
            // CORREÇÃO: Forçamos uma nova transição de estado para limpar a UI antes da próxima análise
            _uiState.value = BioimpedanceState.Idle
            _uiState.value = BioimpedanceState.Loading
            
            val analyzer = BioimpedanceAnalyzerFactory.create(
                apiKey = BuildConfig.GEMINI_API_KEY_1,
                modelId = "gemini-2.5-flash"
            )

            val result = analyzer.analyzeImage(imageBytes)

            result.onSuccess { data ->
                if (data.isSimulated && !BuildConfig.DEBUG) {
                    _uiState.value = BioimpedanceState.Failure("Simulated data rejected in production.")
                } else {
                    _uiState.value = BioimpedanceState.Success(data)
                }
            }.onFailure { error ->
                _uiState.value = BioimpedanceState.Failure(error.message ?: "Unknown Analysis Failure")
            }
        }
    }

    fun transitionToManualEntry() { _uiState.value = BioimpedanceState.ManualEntry }
    fun transitionToRetry() { _uiState.value = BioimpedanceState.RetryAvailable }
    fun resetToIdle() { _uiState.value = BioimpedanceState.Idle }
}
