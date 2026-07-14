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
data class IdCardData(
    val documentNumber: String,      // NUIP, normalized
    val firstName: String,
    val secondName: String?,
    val firstSurname: String,
    val secondSurname: String?,
    val birthDate: LocalDate?,
    val sex: Sex,                    // MALE, FEMALE, UNSPECIFIED
    val bloodType: String?,          // PDF417 only — null for MRZ
    val expirationDate: LocalDate?,  // MRZ only — null for PDF417
    val source: DocumentSource       // PDF417 or MRZ
)
```

Nullability is honest: fields a given source cannot provide are `null` (the
digital card's MRZ does **not** carry blood type).

## Module layout

```
colombian-id-reader/
├── sharedLogic/     KMP library — parsing core (commonMain) + platform scanners
├── sharedUI/        Shared Compose Multiplatform UI (demo/host apps)
├── androidApp/      Android demo app
├── desktopApp/      Desktop (JVM) demo app
└── iosApp/          iOS demo app (Xcode project)
```

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

1. **Phase 1 — Shared parsing core**: characterization tests (golden files from
   the 2020 parser), `Td1MrzParser` with ICAO check digits, clean `Pdf417Parser`
   rewrite (Normalizer / FieldLocator / FieldMapper).
2. **Phase 2 — Android scanner + UI**: CameraX + ML Kit, `IdScannerScreen` with
   `ScanMode.AUTO` (PDF417 first, MRZ fallback).
3. **Phase 3 — iOS scanner + UI**: Vision + AVFoundation, Swift-friendly API.
4. **Phase 4 — Packaging**: Maven/AAR for Android, XCFramework for iOS.
