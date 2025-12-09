/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.components.ds

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import be.sigmadelta.copilotts.sample.ui.theme.DSColors

/**
 * Text style presets following Drivista design system.
 */
sealed class DSTextStyle(
    val fontSize: TextUnit,
    val fontWeight: FontWeight,
    val lineHeight: TextUnit,
    val color: @Composable () -> Color = { DSColors.foreground() }
) {
    /** 48sp, Extra Bold - For splash/hero text */
    data object Hero : DSTextStyle(48.sp, FontWeight.ExtraBold, 56.sp)

    /** 30sp, SemiBold - Section headers */
    data object SectionHeader : DSTextStyle(30.sp, FontWeight.SemiBold, 36.sp)

    /** 22sp, Bold - Large titles */
    data object TitleLarge : DSTextStyle(22.sp, FontWeight.Bold, 28.sp)

    /** 18sp, SemiBold - Medium titles */
    data object TitleMedium : DSTextStyle(18.sp, FontWeight.SemiBold, 24.sp)

    /** 16sp, SemiBold - Small titles */
    data object TitleSmall : DSTextStyle(16.sp, FontWeight.SemiBold, 20.sp)

    /** 16sp, Normal - Body text */
    data object Body : DSTextStyle(16.sp, FontWeight.Normal, 22.sp)

    /** 14sp, Normal - Secondary body text */
    data object BodySmall : DSTextStyle(14.sp, FontWeight.Normal, 18.sp)

    /** 12sp, Normal - Captions and labels */
    data object Caption : DSTextStyle(12.sp, FontWeight.Normal, 16.sp, { DSColors.mutedForeground() })

    /** 10sp, Normal - Fine print */
    data object XSmall : DSTextStyle(10.sp, FontWeight.Normal, 14.sp, { DSColors.mutedForeground() })
}

/**
 * Drivista-styled text component with preset styles.
 */
@Composable
fun DSText(
    text: String,
    modifier: Modifier = Modifier,
    style: DSTextStyle = DSTextStyle.Body,
    color: Color? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        color = color ?: style.color(),
        fontSize = style.fontSize,
        fontWeight = style.fontWeight,
        lineHeight = style.lineHeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}
