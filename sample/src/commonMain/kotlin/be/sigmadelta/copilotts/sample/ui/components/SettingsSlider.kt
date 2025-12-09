/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import be.sigmadelta.copilotts.sample.ui.components.ds.DSText
import be.sigmadelta.copilotts.sample.ui.components.ds.DSTextStyle
import be.sigmadelta.copilotts.sample.ui.theme.DSColors

@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DSText(
                text = label,
                style = DSTextStyle.Body
            )
            DSText(
                text = valueLabel,
                style = DSTextStyle.Body,
                color = DSColors.primaryAccent
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = DSColors.primaryAccent,
                activeTrackColor = DSColors.primaryAccent,
                inactiveTrackColor = DSColors.mutedBackground()
            )
        )
    }
}
