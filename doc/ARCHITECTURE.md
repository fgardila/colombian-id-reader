# colombian-id-reader — Architecture

A Kotlin Multiplatform (KMP) library for reading Colombian identity documents and
returning their data as a structured object. Ships as an **AAR dependency for
Android** and an **XCFramework for iOS**.

It handles both generations of the document:

- **Cédula amarilla (2010s)** — carries a **PDF417** barcode.
- **Cédula digital (2021+)** — carries a **Machine Readable Zone (MRZ, TD1)** and
  no PDF417 barcode.

The library provides the scanning UI and returns a single unified data object,
regardless of which document type was read.

---

## 1. Context & Goals

### Problem

Client apps in Colombia (banking, onboarding, KYC) need to capture identity data
from a citizen's ID. There is no single format: the old yellow card is read via
its PDF417 barcode, while the new digital card must be read via OCR of its MRZ.
A working PDF417 parser exists (Java, 2020) but is single-platform, hard to
maintain, and does not cover the digital card.

### Goals

- One library, two platforms (Android + iOS) from a shared codebase.
- Read **both** document generations behind a single scanning entry point.
- Return **one unified data object** to the client, with honest nullability for
  fields that a given source cannot provide.
- Be **solid, stable, and maintainable** — not a straight port of the 2020 code.
- Handle personal data responsibly: **no persistence, no transmission, no PII
  logging** by default.

### Non-Goals

- No cloud OCR. All recognition is **on-device** (ML Kit on Android, Vision on
  iOS).
- No shared UI framework. UI is **native per platform** (Jetpack Compose on
  Android, SwiftUI-friendly API on iOS).
- The library does not store, upload, or decide what happens to the data after
  it is returned — that is the client app's responsibility.

---

## 2. High-Level Design

### Module layout

```
colombian-id-reader/
├── commonMain/          Pure Kotlin. Testable on the JVM. No platform deps.
│   ├── model/           IdCardData, Sex, DocumentSource, ScanResult, ErrorReason
│   ├── parser/
│   │   ├── Pdf417Parser        Refactored, pattern-based
│   │   └── Td1MrzParser        ICAO 9303 TD1 + check-digit validation
│   └── ColombianIdParser       Public parsing API (String → IdCardData)
│
├── androidMain/         AAR for the Android client
│   ├── scanner/         CameraX + ML Kit (BarcodeScanning + TextRecognition)
│   └── ui/              Composable IdScannerScreen + result callback
│
└── iosMain/             XCFramework for the iOS client
    ├── scanner/         AVFoundation + Vision (VNDetectBarcodes + VNRecognizeText)
    └── ui/              Swift-friendly API, wrapped in SwiftUI by the client
```

### Layering principle

| Layer          | Where          | Responsibility                                   |
|----------------|----------------|--------------------------------------------------|
| Parsing        | `commonMain`   | Pure functions: raw text → `IdCardData`. No I/O. |
| Scanning       | platform       | Camera capture + native barcode/OCR engines.     |
| UI             | platform       | Scanning screen, overlay guide, result delivery. |

The parsers hold the domain logic and are 100% shared and unit-testable without a
device. Everything that touches the camera, the OS OCR engine, or the screen is
platform-specific by necessity and lives in `androidMain` / `iosMain`.

---

## 3. Key Decisions & Trade-offs

### D1 — Parsers in `commonMain`, capture/UI native

**Decision:** Parsing logic is shared Kotlin; capture and UI are native per
platform.

**Why:** The high-value, high-risk logic (interpreting PDF417 and MRZ strings) is
pure and deterministic — ideal for shared code with JVM tests. Camera and OCR,
by contrast, have mature, divergent native engines (ML Kit vs. Vision); wrapping
them in a shared abstraction would add cost without benefit.

**Trade-off:** Scanner and UI code is written twice. Accepted, because it keeps
`commonMain` free of platform dependencies and keeps the iOS binding surface
small and stable.

### D2 — `commonMain` does **not** depend on Compose Multiplatform

**Decision:** UI is native (Compose on Android, SwiftUI on iOS). Shared code is
logic only.

**Why:** Native UI gives each platform an idiomatic scanning experience and keeps
the shared module as a pure logic library — easier to test, version, and bind
into iOS.

**Trade-off:** No pixel-for-pixel shared screen. Accepted; the scanning UI is
small and benefits from platform-native camera integration.

### D3 — Refactor the PDF417 parser; do not port it as-is

**Decision:** Rewrite the 2020 PDF417 logic cleanly rather than translating it
line-by-line to Kotlin.

**Why:** The 2020 code mixes normalization, tokenization, and positional
interpretation with magic indices (`splitStr[6 + corrimiento]`). Preserving it
would fossilize technical debt at the foundation of a brand-new library. The goal
is something solid and maintainable.

**Trade-off & mitigation:** Refactoring risks reintroducing bugs that the old
odd branches (the `corrimiento` shift, the `PubDSK` detection, the three
name/surname cases) already fixed for real-world cards. That knowledge lives only
in the shape of the old code. Mitigation is **characterization testing**: freeze
the old code's outputs on real strings as golden files, then make the new parser
reproduce them (see §6).

### D4 — Locate fields by pattern, not by position

**Decision:** The refactored PDF417 parser identifies each field by *what it
looks like*, not by *where it falls* in the token list.

**Why:** The fragility of the 2020 code is entirely in positional indexing plus a
`corrimiento` counter that shifts indices when a second name/surname is missing.
Replacing "sex is at index 6 + shift" with "the demographic block is the token
matching `\d{2}\d{8}[MF]\d*[OAB+-]+`" eliminates the shift logic.

**Bonus:** This converges with the MRZ parser. Both become
*normalize → locate fields by pattern → map to `IdCardData`*. One shape, two
implementations — internal coherence.

### D5 — On-device OCR only

**Decision:** ML Kit (Android) and Vision (iOS), no cloud.

**Why:** Identity data is sensitive personal data (see §7). Keeping recognition
on-device avoids transmitting PII and removes network dependency and latency.

**Trade-off:** OCR accuracy is bounded by the on-device engine. MRZ **check
digits** (ICAO 9303) mitigate this by detecting misreads deterministically — a
capability the PDF417 format does not offer.

### D6 — No persistence, no logging of PII

**Decision:** The library returns the data object in memory and does nothing
else. It never writes to disk, never sends over the network, and never logs field
values.

**Why:** Habeas Data (Ley 1581 de 2012). The original code logged names and ID
numbers via `Log.d` — that pattern is explicitly removed.

### D7 — Read the PDF417 payload as raw bytes, never as text

**Decision:** Both platforms read the barcode's raw bytes (ML Kit
`Barcode.rawBytes`, Vision `VNBarcodeObservation.payloadData`) decoded as
ISO-8859-1 (a 1:1 byte-to-char mapping), with the text value only as a
fallback.

**Why:** Learned on a real card during Phase 2: the cédula's PDF417 carries
binary sections, so the engines' text-only accessors (`rawValue` /
`payloadStringValue`) return null and every frame is silently discarded.
ISO-8859-1 preserves the ASCII fields the pattern-based locator anchors on.

**Related:** dense PDF417 also needs a high analysis resolution — 1080p proved
marginal on real cards (slow lock-on). Android analyzes at 2560×1440; iOS uses
the 4K session preset (no 1440p preset exists) with a 1080p fallback.

---

## 4. Data Model

A single unified object covers both sources. Nullability is honest: fields a
given source cannot provide are `null`.

```kotlin
data class IdCardData(
    val documentNumber: String,      // NUIP, normalized (no leading zeros)
    val firstName: String,
    val secondName: String?,
    val firstSurname: String,
    val secondSurname: String?,
    val birthDate: LocalDate?,
    val sex: Sex,                     // MALE, FEMALE, UNSPECIFIED
    val bloodType: String?,          // e.g. "O+"; PDF417 only, null for MRZ
    val expirationDate: LocalDate?,  // MRZ only, null for PDF417
    val source: DocumentSource       // PDF417 or MRZ
)

enum class Sex { MALE, FEMALE, UNSPECIFIED }
enum class DocumentSource { PDF417, MRZ }
```

### Field availability by source

| Field            | PDF417 (yellow) | MRZ (digital) |
|------------------|:---------------:|:-------------:|
| documentNumber   | ✅              | ✅            |
| names / surnames | ✅              | ✅            |
| birthDate        | ✅              | ✅            |
| sex              | ✅              | ✅            |
| bloodType (RH)   | ✅              | ❌ (null)     |
| expirationDate   | ❌ (null)       | ✅            |

> **Important for clients:** the digital card's MRZ does **not** carry blood
> type. Apps that rely on RH must account for `bloodType == null` when the source
> is `MRZ`.

---

## 5. Public API & Data Flow

### Parsing API (commonMain, pure)

```kotlin
object ColombianIdParser {
    fun parsePdf417(raw: String): ScanResult
    fun parseMrz(rawLines: List<String>): ScanResult
}

sealed interface ScanResult {
    data class Success(val data: IdCardData) : ScanResult
    data class Error(val reason: ErrorReason) : ScanResult
}

enum class ErrorReason {
    INPUT_TOO_SHORT, PATTERN_NOT_FOUND, CHECK_DIGIT_FAILED, UNKNOWN_FORMAT
}
```

### Scanning UI API (platform)

Android:

```kotlin
IdScannerScreen(
    mode = ScanMode.AUTO,          // PDF417 first, fall back to MRZ
    onResult = { data -> /* IdCardData */ },
    onCancel = { }
)
```

iOS: an equivalent Swift-friendly entry point returning `IdCardData`, wrapped by
the client in a `UIViewControllerRepresentable` for SwiftUI.

### `ScanMode.AUTO`

The single scanning screen serves both cards. Each camera frame is offered to the
barcode detector and, if no PDF417 is found, to the text recognizer looking for
the TD1 pattern. One screen, both document generations.

### Flow

```
Camera frame
   │
   ├─► Barcode detector (PDF417)? ──► raw string ──► ColombianIdParser.parsePdf417
   │                                                          │
   └─► else Text recognizer (MRZ TD1)? ─► 3 lines ─► ColombianIdParser.parseMrz
                                                              │
                                                    ScanResult.Success(IdCardData)
                                                              │
                                                    onResult(...) to client app
```

---

## 6. Parser Details

### 6.1 MRZ (TD1) — new, deterministic

The digital card is ICAO 9303 **TD1**: three lines of 30 characters.

```
Line 1: I<CCOL0000000012<<<<<<<<<<<<<<   doc type, country, document serial
Line 2: 8808213F3101300COL1234567890<9   birth, sex, expiry, NUIP (real cédula)
Line 3: MARTINEZ<GARCIA<MARIA<DANIELA<    surnames << given names
```

Parsing notes:

- **The real cédula number (NUIP)** is the optional-data field on line 2
  (`1234567890`), **not** the document serial on line 1.
- **Birth date** is `YYMMDD`; the century is inferred with a pivot window
  (e.g. `88` → 1988).
- **Names/surnames** on line 3 are split on `<`; filler `<` characters are
  dropped, and `<<` separates surnames from given names.
- **Check digits** (ICAO 9303) validate that OCR did not corrupt the read —
  parse fails with `CHECK_DIGIT_FAILED` rather than returning garbage.
- **Blood type is absent** in the MRZ → `bloodType = null`.

### 6.2 PDF417 — refactored from the 2020 code

Rebuilt around three separated responsibilities, replacing positional indexing
and the `corrimiento` counter with pattern-based field location (see D3, D4):

```
Pdf417Parser
├── Normalizer     cleans the raw string (equivalent of the old replaceAll)
├── FieldLocator   identifies each token by pattern, without magic indices
│                  e.g. "the 10-digit token is the cédula";
│                       "the token matching date+sex+RH is the demographic block"
└── FieldMapper    builds IdCardData
```

Both parsers share the same shape: *normalize → locate by pattern → map*.

---

## 7. Privacy & Compliance

This library handles **sensitive personal data** from a government identity
document, governed in Colombia by **Ley 1581 de 2012 (Habeas Data)**.

Design constraints baked into the library:

- **No persistence.** Nothing is written to disk.
- **No transmission.** Nothing leaves the device.
- **No PII logging.** Field values are never logged (unlike the 2020 code, which
  logged names and ID numbers).
- **In-memory hand-off only.** The library returns `IdCardData` to the client;
  what happens next is the client's responsibility and outside this library's
  scope.

---

## 8. Roadmap / Phases

### Phase 1 — Shared parsing core (`commonMain`) ✅

- **1a — Safety net:** characterization tests. Collect real PDF417 strings
  (2020 production + variants: one name/one surname, two/two, with and without
  `PubDSK`), run the old Java code as an oracle, freeze outputs as golden files.
- **1b — MRZ parser:** new `Td1MrzParser` with ICAO check-digit validation.
  Deterministic, no legacy debt, starts clean.
- **1c — PDF417 refactor:** clean rewrite (Normalizer / FieldLocator /
  FieldMapper), validated against the golden set from 1a.

> Sequence note: doing **1b before 1c** lets the clean parser shape be proven on
> the easy, deterministic case (MRZ) before applying it to the hard case
> (PDF417).

### Phase 2 — Android scanner + UI ✅

CameraX + ML Kit (BarcodeScanning + TextRecognition). Composable
`IdScannerScreen` with an overlay guide and `ScanMode.AUTO`.

Implementation notes: the AUTO sequencing lives in commonMain
(`ScanFrameRouter`, `MrzCandidateExtractor`) so iOS reuses it; barcode
payload read via `rawBytes` (D7); analysis at 2560×1440; single-shot
delivery with a 250ms throttle on the heavy OCR leg; verified against a
real cédula amarilla on device.

### Phase 3 — iOS scanner + UI ✅

Vision + AVFoundation. Swift-friendly API for wrapping in SwiftUI.

Implementation notes: Kotlin/Native in `iosMain`, reusing the shared
router. `VNRecognizeTextRequest` at accurate level with
`usesLanguageCorrection = false` (the fast level and language correction
both mangle OCR-B/MRZ); 4K session preset (D7). **PDF417 is read via
`AVCaptureMetadataOutput`** (the boarding-pass detector): on real cards
Vision's PDF417 decoder never locks onto the cédula's dense barcode —
the Vision leg (`payloadData`, D7) is kept only as fallback. Verified on
a real device for both document generations. The public entry is the
`IdScanner` factory object returning a plain `UIViewController` — Kotlin
subclasses of Objective-C classes cannot be exported to the framework
header. UI strings are constructor parameters with Spanish defaults: a
static framework carries no resource bundle.

### Phase 4 — Packaging & distribution ✅

- **Android: Maven / GitHub Packages.** Coordinates
  `dev.code93:colombian-id-reader` (root KMP module; `-android` AAR,
  `-jvm` for server-side parser reuse, iOS klibs for KMP consumers).
  Version single-sourced from `gradle.properties`. Consumers need a PAT
  with `read:packages` plus `google()`/`mavenCentral()` for transitive
  CameraX/ML Kit. Bundled ML Kit models add ~2–3 MB + ~4 MB/ABI to the
  consumer APK (App Bundle recommended).
- **iOS: XCFramework via SPM.** `Package.swift` at the repo root with a
  `binaryTarget` over the zip attached to each GitHub Release
  (product `SharedLogic`, iOS 15+).
- **Release runbook:** run the `release` GitHub Actions workflow with the
  version number. Ordering matters for the SPM checksum: the zip is built
  once, its checksum committed into `Package.swift`, and the tag created
  on that commit — the manifest at any tag always matches that tag's
  release asset. CI (`ci.yml`) runs JVM/Android tests on Linux and
  Kotlin/Native tests on macOS for every PR.

---

## 9. Open Items

- **Real PDF417 test strings.** Phase 1a depends on access to anonymized real
  strings from 2020. If unavailable, the golden set must be built from the format
  spec via synthetic strings — weaker, but viable.
- **Blood-type gap for digital cards.** Product decision needed on how client
  flows handle `bloodType == null` when reading the new card.
