package com.dino.nanoplayground.ground.ui.viewmodel


import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dino.nanoplayground.ground.models.FeatureAvailability
import com.dino.nanoplayground.ground.models.HomeState
import com.dino.nanoplayground.tools.ToolOrchestrator
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val toolOrchestrator: ToolOrchestrator,
) : ViewModel() {

    val response = mutableStateListOf<String>()
    var homeState = mutableStateOf<HomeState>(HomeState())
        private set

    /** Non-null while the orchestrator is executing a device tool; shown as a UI chip. */
    private val _activeToolCall = MutableStateFlow<String?>(null)
    val activeToolCall = _activeToolCall.asStateFlow()


    init {
        checkForFeatureStatus()
        {
            viewModelScope.launch {
                if (homeState.value.featureAvailability == FeatureAvailability.Available) {
                    collectNanoConfigurations(generativeModel)
                    generativeModel.warmup()
                }
            }
        }
    }


    fun collectNanoConfigurations(generativeModel: GenerativeModel) = viewModelScope.launch {
        homeState.value = homeState.value.copy(
            nanoVersion = generativeModel.getBaseModelName(),
            nanoTokenLimit = generativeModel.getTokenLimit()
        )
    }


    private fun checkForFeatureStatus(onAvailable: () -> Unit) = viewModelScope.launch(
        Dispatchers.IO
    ) {
        val status = generativeModel.checkStatus()

        when (status) {
            FeatureStatus.UNAVAILABLE -> setFeatureAvailability(FeatureAvailability.UnAvailable)

            FeatureStatus.DOWNLOADABLE -> {
                setFeatureAvailability(FeatureAvailability.Available)
                onAvailable()
            }

            FeatureStatus.DOWNLOADING -> {
                setFeatureAvailability(FeatureAvailability.Available)
                onAvailable()
            }

            FeatureStatus.AVAILABLE -> {
                setFeatureAvailability(FeatureAvailability.Available)
                onAvailable()
            }
        }
    }


    fun executePrompt(prompt: String) {
        setInferenceState(true)
        startCountDown()
        checkForFeatureStatus {
            sendRequest(prompt)
        }
    }

    fun sendRequest(prompt: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val text = toolOrchestrator.runWithTools(prompt) { toolName ->
                _activeToolCall.value = "🔧 Using tool: $toolName…"
            }
            _activeToolCall.value = null
            clearAndResetResponse(text)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _activeToolCall.value = null
            setInferenceState(false)
            stopCountDown()
        }
    }


    private val _countDown = MutableStateFlow(0)
    val countDown = _countDown.asStateFlow()
    var countDownJob: Job? = null

    private fun startCountDown() {
        countDownJob?.cancel()
        _countDown.value = 0
        countDownJob = viewModelScope.launch {
            for (i in (1..1000)) {
                delay(1000)
                _countDown.value = i
            }
        }
    }

    private fun stopCountDown() {
        countDownJob?.cancel()
        countDownJob = null
    }

    override fun onCleared() {
        super.onCleared()
        generativeModel.close()
    }


    private fun setInferenceState(state: Boolean) = viewModelScope.launch {
        homeState.value = homeState.value.copy(isInferencing = state)
    }

    private fun clearAndResetResponse(text: String) = viewModelScope.launch {
        response.clear()
        response.add(text)
    }

    private fun setFeatureAvailability(availability: FeatureAvailability) = viewModelScope.launch {
        homeState.value = homeState.value.copy(featureAvailability = availability)
    }


    fun clearModelCache() = viewModelScope.launch(Dispatchers.IO) {
        generativeModel.clearImplicitCaches()
    }

}