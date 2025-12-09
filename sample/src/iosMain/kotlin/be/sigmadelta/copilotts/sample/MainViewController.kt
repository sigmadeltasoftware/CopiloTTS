/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import be.sigmadelta.copilotts.sample.di.sampleModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * Create the main view controller for iOS.
 */
fun MainViewController(): UIViewController {
    // Initialize Napier logging
    Napier.base(DebugAntilog())

    // Start Koin
    startKoin {
        modules(sampleModule)
    }

    return ComposeUIViewController {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            App()
        }
    }
}
