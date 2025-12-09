/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import be.sigmadelta.copilotts.sample.domain.Screen
import be.sigmadelta.copilotts.sample.ui.screens.HomeScreen
import be.sigmadelta.copilotts.sample.ui.screens.ModelsScreen
import be.sigmadelta.copilotts.sample.ui.theme.CopiloTTSTheme
import be.sigmadelta.copilotts.sample.viewmodel.TTSDemoViewModel
import org.koin.compose.koinInject

/**
 * Main app composable.
 */
@Composable
fun App() {
    val viewModel: TTSDemoViewModel = koinInject()
    val state by viewModel.state.collectAsState()

    CopiloTTSTheme {
        when (state.currentScreen) {
            Screen.Home -> {
                HomeScreen(
                    state = state,
                    onTextChange = viewModel::updateText,
                    onSpeak = viewModel::speak,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = viewModel::stop,
                    onVoiceSelect = viewModel::selectVoice,
                    onVoiceStyleSelect = viewModel::selectVoiceStyle,
                    onSpeechRateChange = viewModel::updateSpeechRate,
                    onVolumeChange = viewModel::updateVolume,
                    onClearError = viewModel::clearError,
                    onNavigateToModels = viewModel::navigateToModels
                )
            }
            Screen.Models -> {
                ModelsScreen(
                    availableModels = state.availableModels,
                    downloadedModels = state.downloadedModels,
                    selectedModelId = state.selectedModelId,
                    downloadProgress = state.downloadProgress,
                    currentDownloadingModelId = state.currentDownloadingModelId,
                    onDownload = viewModel::downloadModel,
                    onDelete = viewModel::deleteModel,
                    onSelect = viewModel::selectModel,
                    onBack = viewModel::navigateToHome
                )
            }
        }
    }
}
