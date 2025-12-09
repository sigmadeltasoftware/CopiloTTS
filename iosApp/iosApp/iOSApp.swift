//
//  iOSApp.swift
//  iosApp
//
//  Copyright (c) 2025 Sigma Delta BV
//  Licensed under the MIT License
//

import SwiftUI
import ComposeApp

@main
struct iOSApp: App {

    init() {
        // Register handlers with Kotlin's providers
        // This MUST be called before Koin starts (which happens in MainViewController)
        registerTTSHandler()
        registerHuggingFaceHandler()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }

    private func registerTTSHandler() {
        // Create the native handler implementation
        let handler = TTSNativeHandlerImpl()

        // Register with the Kotlin provider
        // The Kotlin code will retrieve this via Koin
        TTSNativeHandlerProvider.shared.setHandler(handler: handler)
    }

    private func registerHuggingFaceHandler() {
        // Create the HuggingFace ONNX Runtime handler implementation
        let hfHandler = HuggingFaceHandlerImpl()

        // Register with the Kotlin provider for HuggingFace TTS
        HuggingFaceHandlerProvider.shared.setHandler(handler: hfHandler)
    }
}
