# colombian-id-reader 0.2.0 — Capture Gate & Document Routing

> **Status:** in design. Builds on 0.1.0 (shipped, field-tested).
> **Prior art:** see `ARCHITECTURE.md` (0.1.0) for the module layout, data model,
> parser internals, privacy constraints, and packaging. This document covers
> **only what 0.2.0 adds** and does not restate them.

---

## 1. Where 0.1.0 left us

0.1.0 shipped and reads both generations of the Colombian ID end-to-end on
Android and iOS, returning a unified `IdCardData`:

- **Cédula amarilla** → PDF417 barcode → `Pdf417Parser` (clean rewrite of the
  2020 logic, pattern-based field location).
- **Cédula digital** → MRZ TD1 → `Td1MrzParser` (ICAO 9303, check-digit
  validated).
- Parsers live in `commonMain`; camera/OCR and UI are native per platform
  (ML Kit + CameraX on Android, Vision + AVFoundation on iOS). On-device only,
  no persistence, no PII logging.
- Distributed as AAR (Android) and XCFramework (iOS). Tests passing, field
  results positive.

**That foundation is not revisited here.** `IdCardData` and both parsers are
unchanged in 0.2.0.

## 2. Problem 0.2.0 solves

In 0.1.0 the detectors run on **every** camera frame — including frames aimed at
a wall, a hand, a table, or a document too blurry or skewed to read. Three
consequences:

1. **Wasted work.** OCR and barcode detection burn CPU and battery on frames that
   could never yield a result.
2. **No user feedback.** When scanning fails, the screen just… doesn't advance.
   The user isn't told *why* (too far, too tilted, not pointing at the document).
3. **Avoidable false positives.** Random text in view can reach the recognizer.

0.2.0 adds a **capture gate**: confirm a document is actually present and
readable *before* spending any recognition effort — and use that signal to guide
the user.

## 3. Scope

**In scope**

- Capture gate (Stage 1) using **ML Kit / Vision built-ins — no custom model**.
- **Parametrized geometry** via `DocumentFormat` (§4.1) — `Id1` accepted, `Id3`
  defined but not accepted.
- **Declared category** via `ScanMode` (§4.2) — `ColombianId` only.
- Formalized evidence-based routing (Stage 2) behind the gate.
- Scanning-UI feedback driven by gate output.
- Field evaluation to decide whether a custom model is ever needed.

**Out of scope**

- **Passport capture and MRZ TD3 parsing.** Deliberately deferred to 0.3.0.
  0.2.0 defines the `Id3` format and reserves the `Passport` mode so the
  abstractions are shaped correctly, but ships **no** passport capture, TD3
  parser, `PassportData`, curvature/glare handling, or data-page guidance
  (§4.3).
- **Changes to `IdCardData` or the parsers.** None. 0.2.0 is additive.
- **Custom classification model.** Conditional only — see §7.

---

## 4. Design: two-stage capture pipeline

```
Camera frame
   │
   ├─ Stage 1 · GATE                                    ← NEW in 0.2.0
   │    Card-shaped, stable, in-focus object present?
   │      No → UX feedback ("get closer" / "straighten" / "no document").
   │            Discard frame. Do NOT run OCR or barcode detection.
   │      Yes → continue.
   │
   └─ Stage 2 · IDENTIFY BY EVIDENCE                    ← formalized in 0.2.0
        ├─► PDF417 decodes?        → parsePdf417 → CEDULA_AMARILLA
        └─► else MRZ TD1 valid?    → parseMrz    → CEDULA_DIGITAL
                                          │
                                ScanResult.Success(IdCardData) → client
```

Stage 2 is 0.1.0's `ScanMode.AUTO` behavior, unchanged in substance — it simply
now sits behind the gate, and the resolved type is surfaced explicitly. In 0.2.0
that behavior is renamed `ScanMode.ColombianId` (§4.2): the old `AUTO` name
described *how* it worked (try both detectors); the new name describes *what the
client declares* (the category), leaving the auto-detection between amarilla and
digital exactly as it was.

### 4.0 Stage 1 — the gate

- **Engine:** ML Kit Document Scanner / generic object detection (Android);
  Vision equivalent (iOS). Both are built-in. **No training data required.**
- **Checks:** an object matching one of the **accepted `DocumentFormat`s**
  (§4.1) is present, stable across frames, and sufficiently in focus.
- **Output:** a pass/fail signal plus framing hints for the UI overlay.
- **Non-responsibility:** the gate decides *when to read*, never *what the
  document is*. It never labels the document type.

### 4.1 `DocumentFormat` — parametrized geometry

The gate does **not** hardcode card geometry. It validates the candidate object
against a **set of accepted formats**, supplied at construction:

```kotlin
sealed interface DocumentFormat {
    val aspectRatioRange: ClosedRange<Float>

    /** ID-1 — card: 85.6 × 54 mm (≈1.585). Cédula amarilla & digital. */
    data object Id1 : DocumentFormat

    /** ID-3 — passport data page: 125 × 88 mm (≈1.42). Defined, not yet accepted. */
    data object Id3 : DocumentFormat
}
```

```kotlin
// 0.2.0 — this version
CaptureGate(accepts = setOf(DocumentFormat.Id1))

// 0.3.0 — no gate rewrite required
CaptureGate(accepts = setOf(DocumentFormat.Id1, DocumentFormat.Id3))
```

**0.2.0 accepts `Id1` only.** `Id3` is defined so the abstraction is shaped
correctly from the start, but passport capture is **not supported in this
version** — it requires work that is out of scope here (see §4.3 and D10).

### 4.2 `ScanMode` — declared category

The client declares the document **category** before scanning. The gate is
configured from it, and the capture UX can be specific from the first frame
rather than generic until something is detected.

```kotlin
sealed interface ScanMode {
    /** Cédula amarilla or digital — resolved by evidence, not by the user. */
    data object ColombianId : ScanMode

    /** Reserved for 0.3.0. Not implemented in 0.2.0. */
    // data object Passport : ScanMode
}
```

```
ScanMode.ColombianId → gate accepts Id1 → PDF417 | MRZ TD1 → IdCardData
ScanMode.Passport    → gate accepts Id3 → MRZ TD3          → PassportData   [0.3.0]
```

**Division of labour.** The declared mode resolves what a *person* knows without
thinking ("I have a cédula" vs. "I have a passport"). The evidence resolves what
the *machine* knows better than the person (amarilla vs. digital). The user is
never asked a technical question, and D8 is unaffected — within `ColombianId`,
type resolution stays fully evidence-based.

**Who sets the mode.** The SDK exposes the parameter; the **client app** decides
how to obtain it. The library does not ship a "Cédula / Passport" selector
screen — a client that wants one builds it in its own design language. This keeps
the library agnostic of business UX.

**Why a parameter, not a second entry point.** One API surface for clients to
learn; a smaller binding surface in the iOS XCFramework; and the two paths share
~90% of the pipeline (camera, gate, ICAO check digits). The mode is a
configuration, not an identity — these are not two scanners.

### 4.3 What `Id3` does *not* buy

Defining `Id3` opens the hole; it does not deliver passports. Passport capture
additionally requires (all **0.3.0**):

- **Curvature** — an open passport is not flat; it bows toward the spine, exactly
  where the MRZ sits at the foot of the data page.
- **Laminate glare** — airport readers solve this with controlled infrared and
  physical flattening against glass. A handheld phone camera has neither.
- **Page vs. card detection** — an open passport may read as two rectangles.
- **Data-page guidance** — the user must be told to present the data page, not
  the cover. That is UX, not computer vision.
- **Data model** — `IdCardData` assumes a Colombian NUIP and cannot represent a
  foreign passport holder. This is the largest piece of 0.3.0, not the gate.

> If the custom-model escalation of D9 is ever triggered, passports are the most
> likely cause — not cédulas.

### 4.4 Stage 2 — routing

Type is resolved from **extracted evidence**, and exposed to the client:

```kotlin
enum class DocumentType { CEDULA_AMARILLA, CEDULA_DIGITAL }
```

---

## 5. Key decisions

### D7 — Gate before read

**Decision:** Every frame passes the capture gate before any barcode/OCR runs.

**Why:** Cuts wasted recognition on unusable frames, reduces battery drain and
false positives, and — most importantly — produces the signal needed to tell the
user *why* scanning isn't progressing.

**Trade-off:** A gate that is too strict rejects valid documents (false
negatives). Mitigated by threshold tuning against field data (§7) and by keeping
the gate advisory: it gates timing, not interpretation.

### D8 — Identify by evidence, not by an image classifier

**Decision:** Document type comes from what the scan **extracts**, not from
classifying the image up front.

**Why:** An image classifier is probabilistic and can be wrong. The evidence is
deterministic and self-identifying: a PDF417 either decodes or it doesn't; MRZ
line count/length plus ICAO check digits cannot lie. A classifier would add a
failure mode on top of a signal that is already conclusive.

**Corollary:** Even if a custom model is added later (§7), it improves the
**gate** — type identification stays with Stage 2 evidence, permanently.

### D9 — No custom model until the generic gate is proven insufficient

**Decision:** 0.2.0 ships with ML Kit / Vision built-ins only. A custom model is
a **conditional fallback**, not planned work.

**Why:** Training data for identity documents is expensive to collect and
privacy-sensitive, and a custom model adds binary weight and per-platform
divergence. The built-ins may well suffice; that must be measured, not assumed.

> **Note on ML Kit:** ML Kit's vision APIs are conventional computer-vision
> models (detection, classification, OCR) — **not** generative AI. Its object
> detector reports "this resembles category X with confidence N", which is why it
> is fit for gating capture quality but not for concluding document type.

### D10 — Parametrize geometry and mode now; ship Colombia only

**Decision:** 0.2.0 introduces `DocumentFormat` (§4.1) and `ScanMode` (§4.2) as
parametrized abstractions, while accepting **only** `Id1` / `ColombianId`.
`Id3` is defined but not accepted; `Passport` is reserved but not implemented.

**Why:** A gate written as "detects an ID-1 card" hardcodes an assumption that
0.3.0 must break. A gate written as "detects an accepted format" does not. The
change is small while 0.2.0 is in development — compare against a set instead of
a constant — and expensive once the gate ships with the assumption baked in.

**Why declared mode over inference:** Asking the client to declare the category
*removes* the geometry-guessing problem instead of solving it, and lets capture
guidance be specific from the first frame. It also disambiguates the expected
result type before parsing begins.

**Trade-off:** Carrying an unused `Id3` type is mild dead weight, and the
parametrized gate is marginally more complex than a fixed one. Accepted: this is
the same "cheap hole now, impossible later" pattern already applied to raw-fields
vs. country interpretation in the MRZ parser.

**Explicitly not included:** passport capture, TD3 parsing, `PassportData`, and
any change to `IdCardData`. See §4.3.

---

## 6. UX feedback contract

The gate's value is only realized if its output reaches the user. The scanning
overlay consumes gate hints and surfaces guidance:

| Gate condition        | User-facing guidance          |
|-----------------------|-------------------------------|
| No card-shaped object | "Point at the document"       |
| Object too small      | "Get closer"                  |
| Object skewed         | "Straighten the document"     |
| Not in focus / moving | "Hold steady"                 |
| Pass                  | Highlight document, then read |

Wording is the client-app's to localize; the library exposes the conditions.

---

## 7. Field evaluation & the custom-model trigger

0.2.0 includes an explicit measurement phase. The generic gate is deemed
**insufficient** only on unacceptable rates of:

- **False negatives** — a correctly-presented document the gate rejects. The
  costly failure: the user is holding the card correctly and the app won't read.
- **False positives** — a non-document forwarded to OCR. Cheaper (Stage 2 filters
  it) but wasteful.

Only if those rates fail the bar is a custom **capture-quality** model justified.

> **Open:** the numeric thresholds for "unacceptable" are not yet set. This is a
> product/UX decision and blocks the go/no-go in Phase 3 below.

---

## 8. Phases

| # | Phase | Output |
|---|-------|--------|
| 1 | **Capture gate (parametrized)** | Gate on both platforms via ML Kit / Vision built-ins; validates against an accepted `DocumentFormat` set (`Id1` only); pass/fail + framing hints. |
| 2 | **Routing + UX wiring** | `ScanMode.ColombianId` threaded through the pipeline; Stage 2 formalized behind the gate; `DocumentType` exposed; overlay consumes gate hints (§6). |
| 3 | **Field evaluation** | FN/FP rates measured in real conditions → threshold tuning → custom-model go/no-go. |

**Deferred / conditional**

- **Custom capture-quality model** — only if Phase 3 fails the bar (D9).
- **Passport / MRZ TD3 (0.3.0)** — accept `Id3` in the gate, implement
  `ScanMode.Passport`, TD3 parser, and the `ScannedDocument` data-model rework.
  The gate is the small part; the data model is the large one (§4.3).

---

## 9. Open items

- **Gate thresholds** — shape / stability / focus tuning for `Id1`. Set in
  Phase 3. `Id3` tolerances are deliberately left uncalibrated until 0.3.0.
- **"Unacceptable" FN/FP rates** — numeric bar for the custom-model trigger (§7).
- **Blood-type gap** — carried over from 0.1.0 and still unresolved: client flows
  need a product decision for `bloodType == null` on digital cards (the MRZ does
  not carry RH).
