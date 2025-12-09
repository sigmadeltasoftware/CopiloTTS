import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "app.drivista"
version = "1.0.0"

// XCFramework name for SPM distribution
val xcframeworkName = "CopiloTTS"

kotlin {
    // Enable expect/actual classes
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Android + iOS only (no JVM, Linux)
    androidLibrary {
        namespace = "be.sigmadelta.copilotts"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }

    // XCFramework for SPM distribution
    val xcf = XCFramework(xcframeworkName)

    iosX64 {
        binaries.framework {
            baseName = xcframeworkName
            xcf.add(this)
        }
    }
    iosArm64 {
        binaries.framework {
            baseName = xcframeworkName
            xcf.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcframeworkName
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.ktor.client.core)
            implementation(libs.napier)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.onnxruntime.android)
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

mavenPublishing {
    // Publish to Maven Central (requires signing and Sonatype credentials)
    // Configure repository in publishing block below
    publishToMavenCentral()

    // Sign all publications (requires GPG key)
    signAllPublications()

    coordinates(group.toString(), "copilotts", version.toString())

    pom {
        name = "CopiloTTS"
        description = "Kotlin Multiplatform Text-to-Speech SDK with native and HuggingFace model support"
        inceptionYear = "2025"
        url = "https://github.com/sigmadeltasoftware/CopiloTTS"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "sigmadelta"
                name = "Sigma Delta BV"
                url = "https://sigmadelta.be"
            }
        }
        scm {
            url = "https://github.com/sigmadeltasoftware/CopiloTTS"
            connection = "scm:git:git://github.com/sigmadeltasoftware/CopiloTTS.git"
            developerConnection = "scm:git:ssh://git@github.com/sigmadeltasoftware/CopiloTTS.git"
        }
    }
}

// Task to build XCFramework for SPM distribution
tasks.register("buildXCFrameworkForSPM") {
    group = "distribution"
    description = "Builds the XCFramework for Swift Package Manager distribution"
    dependsOn("assemble${xcframeworkName}XCFramework")

    doLast {
        val xcframeworkDir = layout.buildDirectory.dir("XCFrameworks/release").get().asFile
        println("XCFramework built at: ${xcframeworkDir.absolutePath}/$xcframeworkName.xcframework")
        println("Use this path in your Package.swift or copy to your SPM distribution repository")
    }
}

// Task to prepare SPM release (builds XCFramework and generates checksum)
tasks.register<Exec>("zipXCFramework") {
    group = "distribution"
    description = "Creates a ZIP of the XCFramework for distribution"
    dependsOn("buildXCFrameworkForSPM")

    val xcframeworkDir = layout.buildDirectory.dir("XCFrameworks/release").get().asFile
    workingDir = xcframeworkDir
    commandLine("zip", "-r", "$xcframeworkName.xcframework.zip", "$xcframeworkName.xcframework")

    doLast {
        println("\n=== XCFramework ZIP Created ===")
        println("Location: ${xcframeworkDir.absolutePath}/$xcframeworkName.xcframework.zip")
        println("\nTo get the checksum, run:")
        println("  swift package compute-checksum ${xcframeworkDir.absolutePath}/$xcframeworkName.xcframework.zip")
        println("\nUpdate your Package.swift with the URL and checksum:")
        println("  url: \"https://github.com/sigmadeltasoftware/CopiloTTS/releases/download/v$version/$xcframeworkName.xcframework.zip\"")
    }
}
