//
//  ContentView.swift
//  iosApp
//
//  Copyright (c) 2025 Sigma Delta BV
//  Licensed under the MIT License
//

import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // MainViewController is defined in sample module's iosMain
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No-op
    }
}

#Preview {
    ContentView()
}
