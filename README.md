# colombian-id-reader

A **Kotlin Multiplatform (KMP)** library for reading Colombian identity documents
and returning their data as a single structured object. Ships as an **AAR for
Android** and an **XCFramework for iOS**.

It supports both generations of the Colombian cédula:

| Document | Read via |
|---|---|
| **Cédula amarilla** (2010s) | **PDF417** barcode |
| **Cédula digital** (2021+) | **MRZ (TD1, ICAO 9303)** via on-device OCR |

The library provides the scanning UI and returns one unified `IdCardData`
object, regardless of which document type was read.

> Full design rationale, decisions, and trade-offs: [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md)

## Key design points

- **Shared parsing core** (`commonMain`): pure Kotlin, no platform deps, 100%
  unit-testable on the JVM. Both parsers follow the same shape:
  *normalize → locate fields by pattern → map to `IdCardData`*.
- **Native capture & UI per platform**: CameraX + ML Kit on Android (Jetpack
  Compose), AVFoundation + Vision on iOS (SwiftUI-friendly API).
- **On-device recognition only** — no cloud OCR, no network dependency.
- **Privacy by design** (Ley 1581 de 2012 / Habeas Data): no persistence, no
  transmission, no PII logging. The data object is handed off in memory only.

## Data model

```kotlin
sealed interface ScannedDocument {
    val documentType: DocumentType   // CEDULA_AMARILLA | CEDULA_DIGITAL | PASSPORT
    val givenNames: String           // merged: "FABIAN GUILLERMO"
    val surnames: String             // merged: "DE LA OSSA TOVAR"
    val birthDate: LocalDate?
    val sex: Sex                     // MALE, FEMALE, UNSPECIFIED

    data class ColombianId(          // cédula amarilla or digital
        val nuip: String,            // normalized, no leading zeros
        val bloodType: String?,      // amarilla (PDF417) only
        val expirationDate: LocalDate?, // digital (MRZ) only
        /* + common fields */
    ) : ScannedDocument

    data class Passport(             // MRZ TD3, any issuing state
        val passportNumber: String,  // alphanumeric, verbatim
        val issuingState: String,    // ICAO code, may be non-ISO ("D", "XXA")
        val nationality: String,
        val expirationDate: LocalDate,
        val personalNumber: String?, // usually empty
        val namesTruncated: Boolean, // ICAO 39-char limit possibly hit
        /* + common fields */
    ) : ScannedDocument
}
```

Each subtype exposes only the fields its document actually carries; names
are merged strings (neither encoding can reliably split compound names).

> **Reading is not verifying.** A successful passport (or cédula) scan
> returns what is printed. It does not establish that the document is
> genuine or that the bearer is the holder — do not treat a scan as
> identity verification.

## Module layout

```
colombian-id-reader/
├── sharedLogic/     KMP library — parsing core (commonMain) + platform scanners
├── sharedUI/        Shared Compose Multiplatform UI (demo/host apps)
├── androidApp/      Android demo app
├── desktopApp/      Desktop (JVM) demo app
└── iosApp/          iOS demo app (Xcode project)
```

## Installation

### Android (GitHub Packages)

> GitHub Packages requires a personal access token with `read:packages`
> — even for public repositories.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral() // transitive CameraX / ML Kit / Compose deps
        maven {
            url = uri("https://maven.pkg.github.com/fgardila/colombian-id-reader")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// build.gradle.kts (Android app)
dependencies {
    implementation("dev.code93:colombian-id-reader-android:0.1.0")
}
// From a KMP project, depend on the root "dev.code93:colombian-id-reader" instead.
```

Add the camera permission to your manifest: `<uses-permission android:name="android.permission.CAMERA" />`.

**Size note:** the bundled ML Kit models add roughly 2–3 MB (barcode) plus
~4 MB per ABI (text recognition) to the consumer APK. Ship an App Bundle
(or ABI splits) to keep downloads lean.

### iOS (Swift Package Manager)

Add the package `https://github.com/fgardila/colombian-id-reader` in Xcode
(File → Add Package Dependencies) and link the `SharedLogic` product.
Minimum iOS 15. Add `NSCameraUsageDescription` to your Info.plist.

## Usage

Android (Jetpack Compose):

```kotlin
IdScannerScreen(
    // mode = ScanMode.ColombianId is the default (amarilla/digital by evidence)
    onGateHint = { hint -> /* optional: framing guidance for custom UI */ },
    onResult = { data: IdCardData -> /* hand off, in memory only */ },
    onCancel = { /* navigate back */ }
)
```

iOS (SwiftUI via UIViewControllerRepresentable):

```swift
import SharedLogic

func makeUIViewController(context: Context) -> UIViewController {
    let options = IdScannerOptions()          // texts, detectorFilter, onGateHint
    return IdScanner.shared.viewController(
        options: options,
        onResult: { data in /* IdCardData; data.documentType tells which card */ },
        onCancel: { }
    )
}
```

Every frame passes a capture gate before recognition (0.2.0): the bundled
screens show framing guidance ("get closer", "hold steady", …) and clients
with their own UI receive the same `GateHint` conditions via callback.
Both entry points return a single unified `IdCardData` and never persist,
transmit, or log what the camera sees.

### Migrating 0.2.0 → 0.3.0

- **`IdCardData` is now `ScannedDocument`** (sealed): cédulas arrive as
  `ScannedDocument.ColombianId` (`documentNumber` renamed to `nuip`),
  passports as `ScannedDocument.Passport`. Retype your `onResult`
  callbacks and branch on `documentType` (in Swift: cast after switching
  on `documentType` — sealed hierarchies have no exhaustive `switch`).
- `DocumentSource` is gone — use `documentType`.
- `ScanMode.Passport` is new; on iOS set `options.mode =
  ScanModePassport.shared`. `ColombianIdParser.parseMrz` stays TD1-only;
  `parseMrzTd3` is the passport entry point.

### Migrating 0.1.x → 0.2.0

- `ScanMode` is now a sealed interface with `ColombianId` (the old `AUTO`
  semantics, renamed to what the client declares). The debug members
  `PDF417_ONLY`/`MRZ_ONLY` moved to the optional `DetectorFilter` parameter.
- iOS: the string-parameter factory overload was replaced by
  `IdScannerOptions`/`IdScannerTexts` (mutable config objects); the `mode:`
  parameter is gone until `Passport` lands (ColombianId is implied).
- New: `IdCardData.documentType` (`CEDULA_AMARILLA`/`CEDULA_DIGITAL`),
  `onGateHint`, and gate diagnostics in `GateStats`/`ScanDebug`.
- iOS minimum is now **iOS 15** (`VNDetectDocumentSegmentationRequest`).

## Releasing

Run the **release** GitHub Actions workflow with the version number
(e.g. `0.2.0`). It gates on the full test suite, builds and uploads the
XCFramework, rewrites `Package.swift` with the new checksum, tags the
release, and publishes the Maven artifacts to GitHub Packages.

## Building & running

```bash
# Android demo app
./gradlew :androidApp:assembleDebug

# Desktop demo app
./gradlew :desktopApp:run          # or :desktopApp:hotRun --auto for hot reload

# Tests (shared parsing core runs on the JVM)
./gradlew :sharedLogic:jvmTest :sharedUI:jvmTest
./gradlew :sharedLogic:testAndroidHostTest
./gradlew :sharedLogic:iosSimulatorArm64Test
```

iOS: open [iosApp/](iosApp) in Xcode and run from there.

## Roadmap

1. ✅ **Phase 1 — Shared parsing core**: characterization tests (golden files from
   the 2020 parser), `Td1MrzParser` with ICAO check digits, clean `Pdf417Parser`
   rewrite (Normalizer / FieldLocator / FieldMapper).
2. ✅ **Phase 2 — Android scanner + UI**: CameraX + ML Kit, `IdScannerScreen` with
   `ScanMode.AUTO` (PDF417 first, MRZ fallback).
3. ✅ **Phase 3 — iOS scanner + UI**: AVFoundation + Vision, Swift-friendly API.
4. ✅ **Phase 4 — Packaging**: Maven/GitHub Packages for Android, XCFramework/SPM
   for iOS, CI + release automation.
