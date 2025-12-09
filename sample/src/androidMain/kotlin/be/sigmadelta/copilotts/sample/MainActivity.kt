/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import be.sigmadelta.copilotts.engine.NativeTTSEngine
import be.sigmadelta.copilotts.model.ModelStorage
import be.sigmadelta.copilotts.sample.di.sampleModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Napier logging
        Napier.base(DebugAntilog())

        // Set contexts for platform-specific implementations
        NativeTTSEngine.setContext(applicationContext)
        ModelStorage.ContextProvider.setContext(applicationContext)

        // Start Koin
        startKoin {
            androidContext(applicationContext)
            modules(sampleModule)
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                App()
            }
        }
    }
}
