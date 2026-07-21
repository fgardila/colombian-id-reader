# colombian-id-reader 1.0.0 — Document Image Capture

> **Status:** implemented (see *as implemented* notes and §8 D16–D17).
> Builds on 0.1.0, 0.2.0, 0.3.0.
> **Prior art:** `ARCHITECTURE.md` (0.1.0 — module layout, parsers, privacy,
> packaging), `ARCHITECTURE-0.2.0.md` (capture gate, `DocumentFormat`,
> `ScanMode`, evidence-based routing), `ARCHITECTURE-0.3.0.md` (MRZ TD3,
> `ScannedDocument`). This document covers **only what 1.0.0 adds**.

---

## 1. Why this is 1.0.0

0.3.0 already introduced a breaking public API change (`IdCardData` →
`ScannedDocument`), which under SemVer warrants a major. 1.0.0 closes the
0.x line by delivering the last outstanding client requirement — **returning
the document image** — with one further (small) break: `onResult` now
delivers `ScanCapture`. The 1.0.0 surface is the one the library commits to.

This document adds image capture on top of 0.3.0; it does not supersede that
design.

## 2. Goal

A client running a **full registration** flow needs to archive the document
image, not just its data. 1.0.0 lets the SDK return, **on the client's explicit
request**, the captured images alongside the parsed data.

The SDK's existing guarantee is unchanged: it **captures and hands over, never
stores**. Consent and retention remain the client's responsibility (§7).

## 3. Scope

**In scope**

- **Image capture from the existing pipeline** — no new scanner, no new
  dependency (§4).
- **Two-side flow** — front then back, with an explicit state machine (§5).
- **Front-side validation via name cross-check** (§6).
- **Opt-in API** returning `ByteArray` (§4.3).

**Out of scope**

- **Passports** — excluded from image capture (D16): `ScanMode.Passport`
  keeps its 0.3.0 data-page-only behaviour, and `captureImages` is ignored
  in that mode (one-time `ScanDebug` warning).
- **ML Kit Document Scanner** — rejected; see D13.
- **NFC / e-passport chip** — deferred since 0.3.0 §9. Unchanged.
- **Base64 encoding** — rejected; see D14.
- **Authenticity / anti-fraud** — 1.0.0 captures what is presented. It does not
  establish that the document is genuine. The name cross-check is a *linkage*
  signal, not a fraud control (§6.4).
- **Changes to the PDF417, TD1, or TD3 parsers.** Unchanged.

---

## 4. Image capture

### 4.1 Source: the frame that produced the data

The image is **the camera frame the recognizer already succeeded on** — not a
separately captured photo.

This matters beyond convenience: **the image is guaranteed to correspond to the
returned data**, because it is the frame the data came from. A separate capture
step would allow a user to photograph one document and scan another.

Additional consequences:

- No second UI. The existing scanning screen, overlay, and gate are reused.
- Automatic Android/iOS parity — one pipeline, one behaviour.
- **Free crop and perspective correction:** the gate (0.2.0) already located the
  document's edges, and `DocumentFormat` supplies the expected geometry. The
  output is a cropped document, not a photo of a card lying on a desk.

### 4.2 Retention mechanics

Frames are streamed and recycled. By the time the parser reports success, the
frame it consumed **has already been returned to the pool and overwritten**. The
frame must therefore be retained *before* knowing whether it was useful.

**Android (CameraX).** Each frame arrives as an `ImageProxy`, valid only until
`close()`. With `STRATEGY_KEEP_ONLY_LATEST`, the next frame is not delivered
until the current one is closed — failing to close freezes the camera.

```kotlin
imageAnalysis.setAnalyzer(executor) { imageProxy ->
    try {
        if (!gate.passes(imageProxy)) return@setAnalyzer   // finally still closes

        // Retain BEFORE recognizing: copy bytes out of the recycled buffer
        val snapshot = if (captureImages) imageProxy.toJpegBytes() else null

        val result = recognize(imageProxy)
        if (result is Success && snapshot != null) {
            emit(result, snapshot)      // image of the frame that produced the data
        }
    } finally {
        imageProxy.close()              // ALWAYS
    }
}
```

`toJpegBytes()` must **copy**. Holding the `ImageProxy` itself is useless — it
points at a buffer that will be reused. Frames arrive as `YUV_420_888`, so
conversion is required, and `imageInfo.rotationDegrees` must be applied or the
image lands rotated.

> **As implemented — a simplification the design missed:** this pipeline
> processes frames **synchronously inside the camera callback** (that is its
> backpressure model since 0.2.0), so by the time the parser reports success
> the frame is *still open*. There is no retention-before-knowing problem and
> no last-good-frame buffer: the JPEG is encoded on the winning frame right
> there, and on no other frame — encode-on-success (D17). The one exception
> is the iOS PDF417 leg, which arrives via `AVCaptureMetadataOutput` with no
> pixel buffer: there the parsed document is stashed and the **next video
> frame** (~33 ms later; the session keeps running until delivery, and a
> decoded barcode means the card is in view) supplies the back image. Both
> delegates share one serial queue, so this needs no locking. Rotation and
> document cropping use the gate's own box/quad via the pure, unit-tested
> `CropGeometry` (Android additionally maps rotated→sensor coordinates
> because `ImageProxy.toBitmap()` yields the unrotated bitmap).

**iOS (AVFoundation).** Same concept via `CMSampleBuffer`, which is likewise
recycled once the callback returns. The callback runs on the session queue —
blocking it drops frames.

```swift
func captureOutput(_ output: AVCaptureOutput,
                   didOutput sampleBuffer: CMSampleBuffer,
                   from connection: AVCaptureConnection) {
    guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer),
          gate.passes(pixelBuffer) else { return }

    let snapshot = captureImages ? jpegData(from: pixelBuffer) : nil
    let result = recognize(pixelBuffer)
    // ...
}
```

### 4.3 Cost control

JPEG conversion is expensive — doing it per frame at 30fps is not viable. Three
constraints keep it affordable:

- **Only when opted in.** If the client did not request images, cost is zero.
- **Only on frames that passed the gate.** The 0.2.0 gate pays off twice here:
  it has already discarded unusable frames, so only real candidates are encoded.
- **Last-good-frame strategy.** Retain only the most recent gate-passing frame,
  overwriting the previous one. On recognition success, that is the snapshot.
  One buffer, not a queue.

### 4.4 Return type

```kotlin
class DocumentImages(               // as implemented: NOT a data class —
    val front: ByteArray?,          //   ByteArray equality is identity-based and
    val back: ByteArray,            //   generated members invite log leaks
    val format: ImageFormat = JPEG
) {
    fun dispose()                   // zero-fills both buffers, idempotent (§7)
    override fun toString()         // sizes only, never bytes
}
```

Delivered inside the new result wrapper — the 1.0.0 breaking change:

```kotlin
class ScanCapture(
    val document: ScannedDocument,
    val images: DocumentImages?,    // null unless captureImages was requested
    val nameMatch: NameMatch
)
```

`front` is null when its encoding failed (best effort); a failed *back*
encoding yields `images == null` altogether, since the back is the one
mandatory piece — the parsed data still arrives, with the cross-check.

---

## 5. Two-side flow

### 5.1 The asymmetry

Front and back are **not** symmetric in this pipeline:

| | Back | Front |
|---|---|---|
| Contains | PDF417 / MRZ | Photo, signature, printed fields |
| Scanned in 0.1.0–0.3.0 | **Yes** — this is where all data comes from | **Never** |
| Validation available | Strong: barcode decodes, or MRZ check digits pass | Weak: OCR cross-check only (§6) |

"Capture both sides" is therefore **not** "grab two frames". The front is a face
the gate has never seen and for which no conclusive evidence exists. The gate
detects "card-shaped rectangle" — any card satisfies that.

This is why image capture is a **phase**, not a flag: it requires a two-step
state machine, flip guidance, and a genuinely weaker validation path.

### 5.2 Order: front first

**Front → back**, for two reasons:

1. **UX** — a natural "show the front, now flip it" sequence, rather than
   asking the user to go backwards after the important step.
2. **The cross-check requires it** — front names cannot be compared against MRZ
   or PDF417 names until the back has been read. Front-first means both artifacts
   exist by the end of the flow, in the order the user physically handles the card.

```
State machine

FRONT_CAPTURE ──► gate passes ──► retain frame ──► OCR names (best effort)
      │                                                    │
      ▼                                                    ▼
BACK_CAPTURE ──► gate passes ──► retain frame ──► PDF417 | MRZ ──► data
                                                             │
                                                             ▼
                                              CROSS-CHECK front vs. back names
                                                             │
                                                             ▼
                                  ScannedDocument + DocumentImages + NameMatch
```

**As implemented:** the machine is `CaptureFlowController` in `commonMain`
(pure, fully unit-tested), driven by the platform analyzers. It starts in
BACK when capture is not requested — the flow is then byte-identical to
0.3.0. Front capture triggers on a natural gate PASS (3-frame stable
streak); because the front has no recognizer to bail it out of a
miscalibrated gate, ten consecutive *grace* passes (the 0.2.0 4-second
valve) capture anyway — degrading to "slower", never to "stuck". Each side
gets a **fresh `CaptureGate`** (streak/tracking state belongs to one side).
During the front phase the recognizers never run — including the iOS PDF417
metadata leg, which would otherwise read the back's barcode while the user
is still showing the front.

---

## 6. Front validation: name cross-check

### 6.1 Why not "República de Colombia"

Detecting the phrase "REPÚBLICA DE COLOMBIA" on the front was considered and
**rejected as the primary check**. It verifies that *a* Colombian document is
present — not that it is *this* document. The user could present a different
person's card. The phrase also appears on driver's licences and other documents.
Practically, on the cédula digital it is set in small type over security
guilloches, which is poor ground for OCR.

### 6.2 Why the cross-check is better

Comparing the **names OCR'd from the front** against the names already extracted
from the back establishes **linkage**: front and back belong to the same
document. That is a qualitative step up from "a rectangle is present".

It works for both Colombian generations:

- **Cédula amarilla** — PDF417 carries names.
- **Cédula digital** — the MRZ carries names.

### 6.3 Matching must be tolerant

Exact comparison fails by design:

- The MRZ strips diacritics (`MARÍA` → `MARIA`) and folds `Ñ` → `N`. This is an
  ICAO convention, **not** OCR error.
- Front OCR will misread characters (`I`/`l`, `Z`/`2`) over patterned
  backgrounds.

**Normalize first, then measure:**

```
1. Strip diacritics    MARÍA → MARIA,  MUÑOZ → MUNOZ
2. Uppercase
3. Collapse whitespace
4. Then compute Levenshtein distance
```

Normalizing first ensures the measured distance reflects **OCR noise only**, not
transcription conventions — otherwise the tolerance budget is spent on
non-uncertainty.

**Levenshtein distance** = the minimum number of single-character edits
(insertion, deletion, substitution) to turn one string into the other. `MARIA` →
`MARIO` is 1; `MARIA` → `PEDRO` is 5. Roughly 20 lines of pure Kotlin, no
dependency, living in `commonMain` alongside the parsers — consistent with the
"pure logic, JVM-testable" layering.

**Threshold trade-off** — the same FN/FP shape as the 0.2.0 gate:

- Too tight (0–1): legitimate documents rejected over a trivial OCR slip.
- Too loose (5+): `MARIA` and `MARTA` match, and the check becomes meaningless.

Distance should be **length-normalized**: 2 edits in `LI` is enormous; 2 edits in
`MARTINEZ GARCIA` is trivial. Use a similarity ratio rather than a raw count.

### 6.4 The result advises; it does not block

```kotlin
enum class NameMatch { MATCH, MISMATCH, NOT_CHECKED }
```

`NOT_CHECKED` covers the real case where front OCR yields nothing usable.

**The SDK measures; the client decides.** Blocking on mismatch would let an
imperfect OCR reject valid documents and wreck the UX. This mirrors 0.3.0's
`namesTruncated`: surface the uncertainty honestly and let the integrator apply
its own risk policy.

**As implemented** (`NameMatcher`, `commonMain`): hand-written diacritics
fold (no `java.text.Normalizer` in common code) → uppercase → letters and
spaces only → collapsed whitespace → two-row Levenshtein → similarity
`1 - distance/max(len)`. Each of {surnames, given names} is matched against
the best front OCR line — including concatenations of adjacent lines,
because OCR splits long names — and the verdict takes the **worse** of the
two scores: "surname matched, given names garbage" is exactly the
wrong-card scenario and must not pass. Threshold **0.70, internal** (not
public API — it needs field tuning, §10); the exact ratios are emitted via
`ScanDebug` (ratios only, never the names) so field data can drive that
tuning without an API change.

> **Design honestly:** the cross-check is a **signal, not a guarantee**. It runs
> OCR over a security-printed background and will fail sometimes. It is not an
> anti-fraud control.

---

## 7. Privacy — raised stakes

0.1.0's constraints hold: on-device only, no persistence, no transmission, no PII
logging. But the risk profile changes materially and should not be glossed.

Until now the SDK returned **derived data** (a name, a number). It now returns
**the document image**, which includes the holder's **facial photograph** and
**signature**. A facial image is biometric data — a reinforced sensitive category
under Ley 1581 de 2012.

This is lawful, and consent is correctly the client's responsibility. Three
things remain the SDK's:

- **Opt-in by default-off.** Image capture must be an explicit request, never a
  default. A deliberate decision is harder to make by accident.
- **In-memory lifecycle.** A multi-megabyte `ByteArray` containing someone's face
  lingers on the heap until GC. Expose an explicit disposal mechanism and
  document that clients should drop the reference promptly.
- **Keep images out of diagnostics.** Never log image bytes, and ensure they
  cannot reach crash reporters. A large `ByteArray` in a crash dump is a
  disclosure.

---

## 8. Key decisions

### D13 — Reuse the pipeline frame; reject ML Kit Document Scanner

**Decision:** Capture the image from the existing CameraX/AVFoundation pipeline.
Do not adopt ML Kit's Document Scanner.

**Why:** ML Kit Document Scanner is a different product than its name suggests —
a closed, full-screen UI for digitizing paper (receipts, contracts) that launches
its own Activity and returns a JPEG/PDF. Three disqualifiers:

1. **Android-only.** No iOS equivalent; iOS would need
   `VNDocumentCameraViewController` — a different UI, behaviour, and output,
   breaking KMP parity.
2. **It hijacks the flow.** The SDK already owns a scanning screen with an
   overlay and gate; layering Google's Activity on top means two UIs for one
   document.
3. **The frame is already in hand.** The gate has located the document and
   recognition has succeeded on that exact frame.

**Trade-off:** YUV→JPEG conversion, rotation, and cropping must be implemented
per platform rather than inherited. Accepted — the gate already supplies the
geometry, and parity plus data/image correspondence are worth more.

### D14 — Return `ByteArray`, not Base64

**Decision:** `DocumentImages` exposes `ByteArray` (JPEG). Clients needing Base64
convert it themselves.

**Why:**

- **+33% size** — a 1–3 MB document photo becomes 1.3–4 MB.
- **It is a string.** It lives in memory as text, is copied at every hop, and —
  critically — **leaks into logs, crash reports, and network traces trivially**.
  A `ByteArray` in a log is noise; a Base64 blob in a log is a recoverable image
  of someone's identity document.
- **Unnecessary.** Base64 exists to carry binary over text transports (JSON,
  XML). This is an in-process, in-memory hand-off. There is no transport.

Conversion belongs on the client side, where the transport is known.

### D15 — Image capture is a phase, not a flag

**Decision:** Ship image capture as its own workstream with a two-side state
machine, flip guidance, and cross-check — not as a boolean on the existing scan.

**Why:** The back is already scanned and strongly validated; the front is a new
capture surface with no conclusive evidence available (§5.1). The work is a state
machine plus UX plus a tolerant matcher, not a parameter.

### D16 — Passports are excluded from image capture

**Decision (product, at planning):** 1.0.0's image capture applies to
Colombian cédulas only. `ScanMode.Passport` keeps reading the MRZ and
structuring the data exactly as in 0.3.0; `captureImages` is ignored there
(one-time `ScanDebug` warning, `images == null`, `NOT_CHECKED`).

**Why:** the passport flow reads the *data page* — there is no meaningful
"both sides" capture, the cross-check's front/back asymmetry doesn't apply,
and the client requirement driving 1.0.0 (registration archival) is a
cédula flow. Revisit if a data-page-image requirement materializes.

### D17 — Encode on success, not retain-and-hope

**Decision (implementation):** no last-good-frame buffer. Because the
pipeline processes frames synchronously inside the camera callback, the
winning frame is still valid when the parser succeeds — the JPEG is encoded
at that moment and at no other, on both platforms (§4.2 note). The sole
exception, the pixel-less iOS PDF417 metadata callback, defers delivery to
the next video frame instead of keeping a speculative buffer per frame.

**Why:** §4.3's cost constraints fall out for free (zero encodes until
success), and the §4.1 data↔image correspondence is exact rather than
approximate.

---

## 9. Phases

| # | Phase | Output |
|---|-------|--------|
| 1 | ✅ **Frame encoding** | Encode-on-success (D17) + YUV→JPEG + rotation + gate-geometry crop (`CropGeometry`, pure + unit-tested), on both platforms. Opt-in, zero cost when off. |
| 2 | ✅ **Name matcher** (`commonMain`) | `NameMatcher`: normalization (diacritics, case) + length-normalized Levenshtein + 0.70 internal threshold. Pure, JVM-testable, no device. |
| 3 | ✅ **Two-side flow** | `CaptureFlowController` FRONT → BACK, flip guidance ES/EN, front OCR (best effort), `NameMatch` wired into `ScanCapture`. |
| 4 | ✅ **Migration & release** | `ScanCapture` + `DocumentImages` API surface; migration guide (README); ObjC header checks (`ScanCapture`, `frontData`/`backData`, `.match/.mismatch/.notChecked`); 1.0.0 packaging. |

> Sequence rationale: Phase 2 is pure logic with no platform risk and can proceed
> in parallel with Phase 1 — the same reason 0.1.0 built parsers before scanners.
> Phase 3 depends on both.

---

## 10. Open items

- **Levenshtein threshold tuning** — shipped at **0.70** (internal constant,
  deliberately not public API). Needs tuning against real front-side OCR
  output; `ScanDebug` emits the exact per-scan ratios for exactly that.
- **Front OCR reliability** — unmeasured over cédula digital guilloches. May push
  `NOT_CHECKED` rates higher than expected; worth measuring before promising the
  cross-check to clients.
- **Perspective correction** — the iOS quad would allow it; 1.0.0 crops
  axis-aligned on both platforms for parity. Revisit if archival quality asks.
- **`Id3` gate thresholds** — carried from 0.3.0, still uncalibrated.
- **Custom-model bar** — the numeric FN/FP thresholds remain unset (0.2.0 §7).

**Resolved at release:** migration guide (README, 0.3.0 → 1.0.0 — the one
break is `onResult` delivering `ScanCapture`); disposal API =
`DocumentImages.dispose()` (idempotent zero-fill; the iOS `frontData()`/
`backData()` bridges copy, so Swift-held `Data` outlives a dispose).
