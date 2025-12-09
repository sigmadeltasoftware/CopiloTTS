/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import be.sigmadelta.copilotts.TTSEngineType
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import be.sigmadelta.copilotts.sample.domain.TTSUiState
import be.sigmadelta.copilotts.sample.ui.components.SettingsSlider
import be.sigmadelta.copilotts.sample.ui.components.VoiceSelector
import be.sigmadelta.copilotts.sample.ui.components.VoiceStyleSelector
import be.sigmadelta.copilotts.sample.ui.components.ds.*
import be.sigmadelta.copilotts.sample.ui.theme.DSColors
import be.sigmadelta.copilotts.sample.ui.theme.DSCornerRadius
import be.sigmadelta.copilotts.sample.ui.theme.DSPadding
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import copilotts.sample.generated.resources.Res
import copilotts.sample.generated.resources.ic_drivista
import copilotts.sample.generated.resources.ic_sigma_delta
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(
    state: TTSUiState,
    onTextChange: (String) -> Unit,
    onSpeak: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onVoiceSelect: (be.sigmadelta.copilotts.TTSVoice) -> Unit,
    onVoiceStyleSelect: (SupertonicVoiceStyleType) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onClearError: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DSColors.background())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(DSPadding.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(DSPadding.large))

            // Header
            DSText(
                text = "CopiloTTS",
                style = DSTextStyle.Hero,
                color = DSColors.primaryAccent
            )

            Spacer(modifier = Modifier.height(DSPadding.small))

            // Branding section
            HeaderBrandingSection()

            Spacer(modifier = Modifier.height(DSPadding.sectionSpacing))

            // Error banner
            state.error?.let { error ->
                DSCard(
                    variant = DSCardVariant.Outlined,
                    backgroundColor = DSColors.errorRed.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DSText(
                            text = error,
                            modifier = Modifier.weight(1f),
                            style = DSTextStyle.BodySmall,
                            color = DSColors.errorRed
                        )
                        TextButton(onClick = onClearError) {
                            DSText(
                                text = "Dismiss",
                                style = DSTextStyle.BodySmall,
                                color = DSColors.primaryAccent
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(DSPadding.default))
            }

            // Text input
            TextInputSection(
                text = state.text,
                onTextChange = onTextChange
            )

            Spacer(modifier = Modifier.height(DSPadding.default))

            // Playback controls
            PlaybackControls(
                isSpeaking = state.isSpeaking,
                progress = state.progress,
                isPauseSupported = state.isPauseSupported,
                isEnabled = state.isInitialized,
                onSpeak = onSpeak,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop
            )

            Spacer(modifier = Modifier.height(DSPadding.sectionSpacing))

            // Voice selection
            if (state.availableVoices.isNotEmpty()) {
                VoiceSelector(
                    voices = state.availableVoices,
                    selectedVoice = state.selectedVoice,
                    onVoiceSelect = onVoiceSelect,
                    engineType = state.selectedEngine
                )

                Spacer(modifier = Modifier.height(DSPadding.sectionSpacing))
            }

            // Voice style selection (Supertonic only)
            if (state.supportsVoiceStyles && state.availableVoiceStyles.isNotEmpty()) {
                VoiceStyleSelector(
                    availableStyles = state.availableVoiceStyles,
                    selectedStyle = state.selectedVoiceStyle,
                    onStyleSelect = onVoiceStyleSelect
                )

                Spacer(modifier = Modifier.height(DSPadding.sectionSpacing))
            }

            // Settings
            SettingsSection(
                speechRate = state.speechRate,
                volume = state.volume,
                onSpeechRateChange = onSpeechRateChange,
                onVolumeChange = onVolumeChange
            )

            Spacer(modifier = Modifier.height(DSPadding.sectionSpacing))

            // HuggingFace Models section
            HuggingFaceModelsSection(
                downloadedCount = state.downloadedModels.size,
                totalCount = state.availableModels.size,
                selectedModelId = state.selectedModelId,
                selectedEngine = state.selectedEngine,
                onNavigateToModels = onNavigateToModels
            )

            Spacer(modifier = Modifier.height(DSPadding.big))

            // Footer with branding
            FooterSection()

            Spacer(modifier = Modifier.height(DSPadding.default))
        }

        // Loading overlay while initializing
        if (state.isInitializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DSColors.background().copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                DSCard(variant = DSCardVariant.Elevated) {
                    Column(
                        modifier = Modifier.padding(DSPadding.xLarge),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = DSColors.primaryAccent)
                        Spacer(modifier = Modifier.height(DSPadding.default))
                        DSText(
                            text = "Initializing TTS...",
                            style = DSTextStyle.Body
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputSection(
    text: String,
    onTextChange: (String) -> Unit
) {
    DSCard(
        modifier = Modifier.fillMaxWidth(),
        variant = DSCardVariant.Default
    ) {
        Column {
            DSText(
                text = "Text to Speak",
                style = DSTextStyle.TitleSmall
            )
            Spacer(modifier = Modifier.height(DSPadding.small))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                placeholder = {
                    DSText(
                        text = "Enter text to speak...",
                        style = DSTextStyle.Body,
                        color = DSColors.mutedForeground()
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DSColors.primaryAccent,
                    cursorColor = DSColors.primaryAccent,
                    focusedTextColor = DSColors.foreground(),
                    unfocusedTextColor = DSColors.foreground(),
                    unfocusedBorderColor = DSColors.border()
                ),
                shape = RoundedCornerShape(DSCornerRadius.small)
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isSpeaking: Boolean,
    progress: Float,
    isPauseSupported: Boolean,
    isEnabled: Boolean,
    onSpeak: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    DSCard(
        modifier = Modifier.fillMaxWidth(),
        variant = DSCardVariant.Default
    ) {
        Column {
            // Progress bar
            if (isSpeaking) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(DSCornerRadius.xSmall)),
                    color = DSColors.primaryAccent,
                    trackColor = DSColors.mutedBackground()
                )
                Spacer(modifier = Modifier.height(DSPadding.default))
            }

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DSPadding.small)
            ) {
                // Speak button
                DSSolidGradientButton(
                    text = "Speak",
                    modifier = Modifier.weight(1f),
                    enabled = isEnabled && !isSpeaking,
                    onClick = onSpeak
                )

                // Pause/Resume button (if supported)
                if (isPauseSupported) {
                    DSOutlinedButton(
                        text = if (isSpeaking) "Pause" else "Resume",
                        modifier = Modifier.weight(1f),
                        enabled = isEnabled && isSpeaking,
                        onClick = if (isSpeaking) onPause else onResume
                    )
                }

                // Stop button
                DSDestructiveButton(
                    text = "Stop",
                    modifier = Modifier.weight(1f),
                    enabled = isEnabled && isSpeaking,
                    onClick = onStop
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    speechRate: Float,
    volume: Float,
    onSpeechRateChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    DSCard(
        modifier = Modifier.fillMaxWidth(),
        variant = DSCardVariant.Default
    ) {
        Column {
            DSText(
                text = "Settings",
                style = DSTextStyle.TitleSmall
            )

            Spacer(modifier = Modifier.height(DSPadding.default))

            SettingsSlider(
                label = "Speech Rate",
                value = speechRate,
                valueRange = 0.5f..2.0f,
                onValueChange = onSpeechRateChange,
                valueLabel = "${((speechRate * 10).toInt() / 10.0)}x"
            )

            SettingsSlider(
                label = "Volume",
                value = volume,
                valueRange = 0.0f..1.0f,
                onValueChange = onVolumeChange,
                valueLabel = "${(volume * 100).toInt()}%"
            )
        }
    }
}

@Composable
private fun HuggingFaceModelsSection(
    downloadedCount: Int,
    totalCount: Int,
    selectedModelId: String?,
    selectedEngine: TTSEngineType,
    onNavigateToModels: () -> Unit
) {
    DSCard(
        modifier = Modifier.fillMaxWidth(),
        variant = DSCardVariant.Default
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    DSText(
                        text = "HuggingFace Models",
                        style = DSTextStyle.TitleSmall
                    )
                    DSText(
                        text = "$downloadedCount of $totalCount models downloaded",
                        style = DSTextStyle.Caption
                    )
                }
            }

            Spacer(modifier = Modifier.height(DSPadding.medium))

            // Current engine indicator - styled like selected ModelCard
            val isHuggingFace = selectedEngine == TTSEngineType.HUGGINGFACE && selectedModelId != null
            DSCard(
                variant = DSCardVariant.Outlined,
                backgroundColor = if (isHuggingFace)
                    DSColors.selectedCardBackground()
                else
                    DSColors.cardBackground(),
                contentPadding = PaddingValues(DSPadding.medium),
                borderColor = if (isHuggingFace) DSColors.primaryAccent else DSColors.border()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        DSText(
                            text = "Current Engine",
                            style = DSTextStyle.Caption
                        )
                        DSText(
                            text = if (isHuggingFace) "HuggingFace" else "Native TTS",
                            style = DSTextStyle.Body
                        )
                        if (isHuggingFace && selectedModelId != null) {
                            DSText(
                                text = selectedModelId.substringAfterLast("/"),
                                style = DSTextStyle.BodySmall,
                                color = DSColors.primaryAccent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(DSPadding.medium))

            DSGradientButton(
                text = "Browse & Download Models",
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToModels
            )
        }
    }
}

@Composable
private fun HeaderBrandingSection() {
    val uriHandler = LocalUriHandler.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DSPadding.small)
    ) {
        // Sigma Delta branding
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSCornerRadius.small))
                .clickable { uriHandler.openUri("https://sigmadelta.be") }
                .padding(horizontal = DSPadding.small, vertical = DSPadding.xSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSPadding.small)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_sigma_delta),
                contentDescription = "Sigma Delta Logo",
                modifier = Modifier.size(20.dp)
            )
            DSText(
                text = "Made with care by Sigma Delta",
                style = DSTextStyle.Caption
            )
        }

        // Drivista branding
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(DSCornerRadius.small))
                .clickable { uriHandler.openUri("https://drivista.app") }
                .padding(horizontal = DSPadding.small, vertical = DSPadding.xSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DSPadding.small)
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_drivista),
                contentDescription = "Drivista Logo",
                modifier = Modifier.size(18.dp)
            )
            DSText(
                text = "As used in Drivista",
                style = DSTextStyle.Caption,
                color = DSColors.primaryAccent
            )
        }
    }
}

@Composable
private fun FooterSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DSText(
            text = "MIT License",
            style = DSTextStyle.XSmall
        )
    }
}
