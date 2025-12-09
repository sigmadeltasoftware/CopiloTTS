// swift-tools-version:5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "CopiloTTS",
    platforms: [
        .iOS(.v14),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "CopiloTTS",
            targets: ["CopiloTTS"]
        ),
    ],
    targets: [
        // Binary target for the XCFramework
        // Update the URL and checksum when releasing a new version
        .binaryTarget(
            name: "CopiloTTS",
            // For local development, use path:
            // path: "library/build/XCFrameworks/release/CopiloTTS.xcframework"

            // For release distribution, use url and checksum:
            url: "https://github.com/sigmadeltasoftware/CopiloTTS/releases/download/v1.0.0/CopiloTTS.xcframework.zip",
            checksum: "REPLACE_WITH_ACTUAL_CHECKSUM"
        ),
    ]
)
