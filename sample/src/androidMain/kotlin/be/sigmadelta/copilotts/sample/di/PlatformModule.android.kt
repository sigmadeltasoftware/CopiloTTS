/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.di

import org.koin.core.module.Module

/**
 * Android doesn't need any additional platform modules.
 * TTSNativeHandler is not used on Android.
 */
actual fun platformModules(): List<Module> = emptyList()
