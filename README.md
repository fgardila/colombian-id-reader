# colombian-id-reader

A **Kotlin Multiplatform (KMP)** library for reading Colombian identity documents
and returning their data as a single structured object — optionally together
with **JPEG images of both sides of the document** (1.0.0). Ships as an **AAR
for Android** and an **XCFramework for iOS**.

It supports both generations of the Colombian cédula, plus machine-readable
passports:

| Document | Read via | Image capture |
|---|---|---|
| **Cédula amarilla** (2010s) | **PDF417** barcode | front + back (opt-in) |
| **Cédula digital** (2021+) | **MRZ (TD1, ICAO 9303)** via on-device OCR | front + back (opt-in) |
| **Passport** (any state) | **MRZ (TD3, ICAO 9303)** via on-device OCR | — (data page only) |

The library provides the scanning UI and returns one `ScanCapture` holding the
parsed document, the images (when requested), and a front/back name
cross-check.

> Full design rationale, decisions, and trade-offs: [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md)
> and the per-version documents (`doc/ARCHITECTURE-*.md`).

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
class ScanCapture(
    val document: ScannedDocument,   // the parsed data
    val images: DocumentImages?,     // null unless captureImages was requested
    val nameMatch: NameMatch         // MATCH | MISMATCH | NOT_CHECKED
)

class DocumentImages(
    val front: ByteArray?,           // JPEG; null if front encoding failed
    val back: ByteArray,             // JPEG of the frame the data came from
    val format: ImageFormat          // JPEG
) { fun dispose() /* zero-fills both buffers */ }

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

The images are the camera frames the recognizers actually worked on —
cropped to the document and rotated upright — so the image is guaranteed to
correspond to the returned data. `nameMatch` compares the names OCR'd from
the front against the names decoded from the back (diacritics-normalized,
length-normalized Levenshtein): a *linkage* signal that front and back
belong to the same document. It advises; it never blocks — apply your own
risk policy, and expect `NOT_CHECKED` when the front OCR yields nothing
usable.

> **Reading is not verifying.** A successful passport (or cédula) scan
> returns what is printed. It does not establish that the document is
> genuine or that the bearer is the holder — do not treat a scan as
> identity verification. The name cross-check is not an anti-fraud control.

> **Privacy — raised stakes with images (1.0.0).** The captured images
> include the holder's **facial photograph and signature** — biometric
> data, a reinforced sensitive category under Ley 1581 de 2012. The SDK
> captures and hands over, never stores; consent and retention are your
> responsibility. Image capture is **off by default**; when you use it,
> drop references promptly, call `DocumentImages.dispose()` once
> persisted or discarded, and never let the bytes reach logs or crash
> reporters.

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
    implementation("dev.code93:colombian-id-reader-android:1.0.0")
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
    captureImages = true,   // opt-in: front→back flow + images + name cross-check
    onGateHint = { hint -> /* optional: framing guidance for custom UI */ },
    onResult = { capture: ScanCapture ->
        val document = capture.document      // ScannedDocument
        val images = capture.images          // DocumentImages? (JPEGs)
        // persist or discard, then: images?.dispose()
    },
    onCancel = { /* navigate back */ }
)
```

iOS (SwiftUI via UIViewControllerRepresentable):

```swift
import SharedLogic

func makeUIViewController(context: Context) -> UIViewController {
    let options = IdScannerOptions()          // texts, detectorFilter, onGateHint
    options.captureImages = true              // opt-in, cédula modes only
    return IdScanner.shared.viewController(
        options: options,
        onResult: { capture in
            let document = capture.document   // branch on documentType, then cast
            if let images = capture.images {
                let front = DocumentImagesNSDataKt.frontData(images: images) // NSData
                let back  = DocumentImagesNSDataKt.backData(images: images)
                // persist or discard, then: images.dispose()
            }
        },
        onCancel: { }
    )
}
```

Every frame passes a capture gate before recognition (0.2.0): the bundled
screens show framing guidance ("get closer", "hold steady", …) and clients
with their own UI receive the same `GateHint` conditions via callback. With
`captureImages` the cédula flow guides the user front-first ("Muestre el
frente…", "Ahora voltee el documento") and the back side then behaves
exactly as before. Both entry points deliver a single `ScanCapture` and
never persist, transmit, or log what the camera sees.

### Migrating 0.3.0 → 1.0.0

- **`onResult` now delivers `ScanCapture`** instead of `ScannedDocument` —
  the only breaking change. Your document is `capture.document`; if you
  don't request images, `capture.images` is null and
  `capture.nameMatch == NOT_CHECKED`, and the scan flow, cost and latency
  are unchanged.
- New opt-ins: `IdScannerScreen(captureImages = true)` on Android,
  `options.captureImages = true` on iOS. Cédula modes only — passport
  scans stay data-page only and ignore the flag.
- Parsers, `ScannedDocument`, `ScanMode` and the gate are untouched.

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
5. ✅ **0.2.0 — Capture gate**: evidence-based "when to read" (framing, focus,
   stability) with UX hints and a false-negative grace valve.
6. ✅ **0.3.0 — Passports**: MRZ TD3 (any issuing state), `ScannedDocument` model.
7. ✅ **1.0.0 — Document images**: front→back capture flow, JPEGs of the frames
   the data came from, front/back name cross-check (Levenshtein).
