import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String

// Compose lives only in androidMain (scanning UI); keep the compose
// compiler away from the ios/jvm compilations, which have no runtime.
composeCompiler {
    targetKotlinPlatforms.set(setOf(KotlinPlatformType.androidJvm))
}

kotlin {
    val xcf = XCFramework("SharedLogic")
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedLogic"
            isStatic = true
            export(libs.kotlinx.datetime)
            xcf.add(this)
        }
    }
    
    jvm()
    
    androidLibrary {
       namespace = "dev.code93.colombian_id_reader.sharedLogic"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       aarMetadata {
           minCompileSdk = libs.versions.android.compileSdk.get().toInt()
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        androidMain.dependencies {
            // Camera capture
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            // On-device recognition (bundled models, no Play Services app)
            implementation(libs.mlkit.barcodeScanning)
            implementation(libs.mlkit.objectDetection)
            implementation(libs.mlkit.textRecognition)
            implementation(libs.kotlinx.coroutinesPlayServices)
            // Scanning screen (native Compose, androidMain only — D2)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
    }
}

publishing {
    // The Gradle module stays :sharedLogic (the demo apps and the Xcode
    // build phase reference it); only the published coordinates change.
    // configureEach keeps the root module's Gradle Module Metadata
    // ("available-at") consistent with the per-target artifactIds.
    publications.withType<MavenPublication>().configureEach {
        artifactId = artifactId.replace(project.name, "colombian-id-reader")
        pom {
            name.set("colombian-id-reader")
            description.set(
                "Kotlin Multiplatform library for reading Colombian identity " +
                    "documents (PDF417 cedula amarilla + MRZ cedula digital)"
            )
            url.set("https://github.com/fgardila/colombian-id-reader")
            scm {
                url.set("https://github.com/fgardila/colombian-id-reader")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/fgardila/colombian-id-reader")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String?
            }
        }
    }
}