/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import be.sigmadelta.copilotts.TTSEngineType
import be.sigmadelta.copilotts.TTSVoice
import be.sigmadelta.copilotts.sample.domain.usecase.VoiceSelectionUseCase
import be.sigmadelta.copilotts.sample.domain.usecase.getBaseLanguage
import be.sigmadelta.copilotts.sample.domain.usecase.sortedByLanguagePriority
import be.sigmadelta.copilotts.sample.ui.components.ds.*
import be.sigmadelta.copilotts.sample.ui.theme.DSColors
import be.sigmadelta.copilotts.sample.ui.theme.DSCornerRadius
import be.sigmadelta.copilotts.sample.ui.theme.DSPadding
import be.sigmadelta.copilotts.sample.ui.utils.FlagUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelector(
    voices: List<TTSVoice>,
    selectedVoice: TTSVoice?,
    onVoiceSelect: (TTSVoice) -> Unit,
    modifier: Modifier = Modifier,
    engineType: TTSEngineType = TTSEngineType.NATIVE
) {
    // Use different UI for HuggingFace models (single model with speakers)
    if (engineType == TTSEngineType.HUGGINGFACE) {
        HuggingFaceVoiceSelector(
            voices = voices,
            selectedVoice = selectedVoice,
            onVoiceSelect = onVoiceSelect,
            modifier = modifier
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }

    // Preferred country codes for each prominent language
    val preferredCountryCodes = mapOf(
        "en" to "US",
        "es" to "ES",
        "zh" to "CN",
        "hi" to "IN",
        "fr" to "FR"
    )

    // Get preferred voice for each of the top 5 languages (if available)
    // Prefer the canonical country variant for each language
    val prominentVoices = remember(voices) {
        VoiceSelectionUseCase.LANGUAGE_PRIORITY.mapNotNull { lang ->
            val preferredCountry = preferredCountryCodes[lang]
            // First try to find the preferred country variant
            voices.firstOrNull {
                it.languageCode.equals("$lang-$preferredCountry", ignoreCase = true)
            } ?: voices.firstOrNull {
                it.languageCode.equals("${lang}_$preferredCountry", ignoreCase = true)
            } ?: voices.firstOrNull {
                it.getBaseLanguage() == lang
            }
        }
    }

    // All other voices not in the prominent list
    val otherVoices = remember(voices, prominentVoices) {
        val prominentIds = prominentVoices.map { it.id }.toSet()
        voices.filter { it.id !in prominentIds }.sortedByLanguagePriority()
    }

    // Check if selected voice is in "other" category
    val selectedIsOther = remember(selectedVoice, prominentVoices) {
        selectedVoice != null && prominentVoices.none { it.id == selectedVoice.id }
    }

    DSCard(
        modifier = modifier.fillMaxWidth(),
        variant = DSCardVariant.Default
    ) {
        Column {
            DSText(
                text = "Voice",
                style = DSTextStyle.TitleSmall
            )
            DSText(
                text = "Select a text-to-speech voice",
                style = DSTextStyle.Caption
            )

            Spacer(modifier = Modifier.height(DSPadding.default))

            // Prominent language radio buttons
            prominentVoices.forEach { voice ->
                val isSelected = selectedVoice?.id == voice.id
                val baseLang = voice.getBaseLanguage()
                // Always show the canonical flag for prominent languages
                val canonicalCountry = preferredCountryCodes[baseLang]
                val flag = if (canonicalCountry != null) {
                    FlagUtils.getFlagEmoji("$baseLang-$canonicalCountry")
                } else {
                    FlagUtils.getFlagEmoji(voice.languageCode)
                }
                val displayName = VoiceSelectionUseCase.LANGUAGE_DISPLAY_NAMES[baseLang] ?: voice.name

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DSCornerRadius.small))
                        .clickable { onVoiceSelect(voice) }
                        .padding(vertical = DSPadding.small, horizontal = DSPadding.xSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onVoiceSelect(voice) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = DSColors.primaryAccent,
                            unselectedColor = DSColors.mutedForeground()
                        )
                    )
                    Spacer(modifier = Modifier.width(DSPadding.small))
                    DSText(
                        text = flag,
                        style = DSTextStyle.TitleMedium
                    )
                    Spacer(modifier = Modifier.width(DSPadding.medium))
                    DSText(
                        text = displayName,
                        style = DSTextStyle.Body,
                        color = if (isSelected) DSColors.primaryAccent else DSColors.foreground()
                    )
                }
            }

            // Other voices dropdown (if any exist)
            if (otherVoices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(DSPadding.medium))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = DSPadding.small),
                    color = DSColors.border()
                )

                DSText(
                    text = "Other languages",
                    style = DSTextStyle.Caption
                )

                Spacer(modifier = Modifier.height(DSPadding.small))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedIsOther && selectedVoice != null) {
                            "${FlagUtils.getFlagEmoji(selectedVoice.languageCode)} ${selectedVoice.name}"
                        } else {
                            "Select another language..."
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DSColors.primaryAccent,
                            unfocusedBorderColor = DSColors.border(),
                            focusedLabelColor = DSColors.primaryAccent,
                            focusedTextColor = DSColors.foreground(),
                            unfocusedTextColor = DSColors.foreground()
                        ),
                        shape = RoundedCornerShape(DSCornerRadius.small)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        otherVoices.forEach { voice ->
                            val flag = FlagUtils.getFlagEmoji(voice.languageCode)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(DSPadding.medium),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DSText(
                                            text = flag,
                                            style = DSTextStyle.TitleMedium
                                        )
                                        Column {
                                            DSText(
                                                text = voice.name,
                                                style = DSTextStyle.Body
                                            )
                                            DSText(
                                                text = voice.languageCode,
                                                style = DSTextStyle.Caption
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onVoiceSelect(voice)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Voice selector for HuggingFace models.
 * Displays model-specific voices (speakers/styles) as simple radio buttons.
 */
@Composable
private fun HuggingFaceVoiceSelector(
    voices: List<TTSVoice>,
    selectedVoice: TTSVoice?,
    onVoiceSelect: (TTSVoice) -> Unit,
    modifier: Modifier = Modifier
) {
    if (voices.isEmpty()) return

    // Get model name from first voice's metadata
    val modelName = voices.firstOrNull()?.metadata?.get("modelId")?.substringAfterLast("/") ?: "Model"
    val architecture = voices.firstOrNull()?.metadata?.get("architecture") ?: ""

    DSCard(
        modifier = modifier.fillMaxWidth(),
        variant = DSCardVariant.Default
    ) {
        Column {
            DSText(
                text = "Voice",
                style = DSTextStyle.TitleSmall
            )
            DSText(
                text = when (architecture) {
                    "PIPER" -> "Select a speaker for $modelName"
                    "SUPERTONIC" -> "Select a voice style"
                    else -> "Select a voice for $modelName"
                },
                style = DSTextStyle.Caption
            )

            Spacer(modifier = Modifier.height(DSPadding.default))

            // Show all voices as radio buttons
            voices.forEach { voice ->
                val isSelected = selectedVoice?.id == voice.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DSCornerRadius.small))
                        .clickable { onVoiceSelect(voice) }
                        .padding(vertical = DSPadding.small, horizontal = DSPadding.xSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onVoiceSelect(voice) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = DSColors.primaryAccent,
                            unselectedColor = DSColors.mutedForeground()
                        )
                    )
                    Spacer(modifier = Modifier.width(DSPadding.small))
                    Column {
                        DSText(
                            text = voice.name,
                            style = DSTextStyle.Body,
                            color = if (isSelected) DSColors.primaryAccent else DSColors.foreground()
                        )
                        // Show description from metadata if available
                        voice.metadata?.get("description")?.let { description ->
                            DSText(
                                text = description,
                                style = DSTextStyle.Caption
                            )
                        }
                    }
                }
            }
        }
    }
}
