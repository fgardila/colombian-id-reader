# colombian-id-reader 0.3.0 — Passport Support (MRZ TD3)

> **Status:** implemented (Phases 1-3); Id3 field evaluation (Phase 4) pending.
> Version label decision (§10): shipped as **0.3.0** — the breaking model
> change is communicated in the README migration guide. iOS mode selection
> shipped as `IdScannerOptions.mode` (property, not a factory overload).
> **Prior art:** `ARCHITECTURE.md` (0.1.0 — module layout, parsers, privacy,
> packaging) and `ARCHITECTURE-0.2.0.md` (capture gate, `DocumentFormat`,
> `ScanMode`, evidence-based routing). This document covers **only what 0.3.0
> adds** and does not restate them.

---

## 1. Where 0.2.0 leaves us

- **0.1.0** reads both Colombian ID generations end-to-end on Android and iOS
  (PDF417 → cédula amarilla, MRZ TD1 → cédula digital), returning `IdCardData`.
- **0.2.0** adds the capture gate (Stage 1) before recognition, formalizes
  evidence-based routing (Stage 2), and — critically for this version —
  **parametrizes the abstractions**: `DocumentFormat` (`Id1` accepted, `Id3`
  defined) and `ScanMode` (`ColombianId` only, `Passport` reserved).

0.2.0 deliberately opened the holes this version fills. The gate does not need a
rewrite; the data model does.

## 2. Goal

Let a client app read a **foreign visitor's passport** to speed up entry, on the
same SDK, on both platforms. Passport reading is **opt-in**: the client app
declares it via `ScanMode.Passport` (§4.1). The library never guesses.

## 3. Scope

**In scope**

- **MRZ TD3 parser** — 2 lines × 44 chars, ICAO 9303 Part 4.
- **`ScannedDocument` data model** — sealed hierarchy replacing the bare
  `IdCardData` return (§5). **Breaking change.**
- **`ScanMode.Passport`** — activates `Id3` in the gate and the TD3 path.
- **Passport capture** — `Id3` geometry, curvature/glare tolerance, data-page
  guidance (§6).
- **Any-country support** — nationality, ICAO non-ISO country codes,
  transliteration, truncation (§4.3).

**Out of scope**

- **Cédula de extranjería** — a distinct Colombian document with its own format.
  Not TD3. Not covered here.
- **NFC / e-passport chip (BAC/PACE)** — the chip holds the authoritative data
  and a face image, but requires the MRZ key, NFC stacks per platform, and
  cryptographic protocol work. The MRZ printed on the data page is what 0.3.0
  reads. See §9.
- **Passport authenticity / anti-fraud** — 0.3.0 reads what is printed. It does
  not verify the document is genuine (§7).
- **Changes to the PDF417 or TD1 parsers.** Unchanged.

---

## 4. MRZ TD3

### 4.1 Structure

Two lines of 44 characters (vs. TD1's 3 × 30):

```
P<COLMARTINEZ<GARCIA<<MARIA<DANIELA<<<<<<<<<<
AB1234567<1COL8808213F3101300<<<<<<<<<<<<<<02
│         │ │  │      │ │      │            │
│         │ │  │      │ │      │            └─ composite check
│         │ │  │      │ │      └─ optional data (personal no.) + check
│         │ │  │      │ └─ expiry YYMMDD + check
│         │ │  │      └─ sex
│         │ │  └─ birth YYMMDD + check
│         │ └─ nationality
│         └─ passport no. check digit
└─ passport number
```

Line 1: document type (`P`), issuing state, then full name.
Line 2: the fixed data block above.

### 4.2 Why TD3 is *easier* than TD1 was

This is the key insight shaping this version's cost:

| | TD1 (cédula) | TD3 (passport) |
|---|---|---|
| Real document number | **Varies by country** — Colombia puts the NUIP in optional data; Spain the DNI; Argentina uses line 1 | **Always** the passport number, fixed position, same meaning everywhere |
| Name separator | Colombia deviates: single `<` | Universal `<<`, respected |
| Country profiles needed | Yes (that is why multi-country TD1 was rejected) | **No** — one parser, all countries |

**Passports are the case where the standard is genuinely honoured.** A passport
must be machine-readable in Frankfurt, Tokyo, and Bogotá; a country that
tropicalized its MRZ would strand its own citizens at borders. ICAO 9303 is
treaty-backed, audited, and non-MRZ passports stopped being valid in 2015.
Interoperability is enforced by necessity, not goodwill.

Consequence: the multi-country objection that killed universal TD1 **does not
apply here**. No `CountryProfile` layer is needed for TD3.

### 4.3 What does vary (and must be handled)

- **Transliteration** — Cyrillic, Arabic, Chinese names are romanized per ICAO
  rules. The MRZ is always A–Z; the printed name may differ from the MRZ name.
- **Truncation** — names longer than 39 chars are cut. Already observed in the
  Argentine DNI sample (`MARIANA<FL`). A truncated name is **correct MRZ data**,
  not a parse failure — it must not be treated as an error.
- **Non-ISO country codes** — ICAO defines its own: `D` (Germany), `GBR`
  subtypes, `XXA`/`XXB`/`XXC` (stateless, refugee), UN and Vatican issuers.
  A plain ISO-3166 lookup will fail on these.
- **Optional data** — usually empty; some states place a personal number there.
  Never assume it is populated.

None of these break structural parsing. All of them break naive assumptions.

---

## 5. Data model — `ScannedDocument`

### 5.1 The problem

`IdCardData` encodes "a Colombian identified by NUIP, with a blood type". A
passport has no NUIP and no RH, and its holder has a foreign nationality. The
type cannot represent it without lying.

### 5.2 Decision (D11)

**Decision:** Introduce a sealed hierarchy. Each document exposes only the fields
it actually carries.

```kotlin
sealed interface ScannedDocument {
    val documentType: DocumentType
    val givenNames: String
    val surnames: String
    val birthDate: LocalDate?
    val sex: Sex

    data class ColombianId(
        override val documentType: DocumentType,   // CEDULA_AMARILLA | CEDULA_DIGITAL
        val nuip: String,
        val bloodType: String?,                    // PDF417 only
        val expirationDate: LocalDate?,            // MRZ only
        // ... plus the common fields
    ) : ScannedDocument

    data class Passport(
        override val documentType: DocumentType,   // PASSPORT
        val passportNumber: String,
        val issuingState: String,                  // ICAO code, may be non-ISO
        val nationality: String,                   // ICAO code, may be non-ISO
        val expirationDate: LocalDate,
        val personalNumber: String?,               // optional data, often absent
        val namesTruncated: Boolean,               // ICAO 39-char limit hit
        // ... plus the common fields
    ) : ScannedDocument
}

enum class DocumentType { CEDULA_AMARILLA, CEDULA_DIGITAL, PASSPORT }
```

**Why:** Nullable-everything (`bloodType`, `nuip`, `passportNumber` all optional
on one flat type) would force clients to know which combinations are valid — the
exact class of debt rejected when the 2020 PDF417 code was refactored rather than
ported. A library meant to be solid should not ship a type that lies at birth.

**Trade-off — this is a breaking change.** Clients on 0.1.0/0.2.0 receive
`IdCardData`; they will receive `ScannedDocument`. Their callbacks and `when`
blocks change. Accepted deliberately: the cost is paid once, now, while the
client base is small.

> **SemVer note.** A breaking public API change means this is properly **1.0.0**,
> not 0.3.0. The version label is a product decision; the compatibility impact is
> the same either way and should be communicated to integrators as such.

### 5.3 iOS binding caveat

Kotlin `sealed` does not export to Swift with exhaustiveness — Swift sees classes
without a compiler-checked `switch`. Mitigation: expose `documentType` (an enum)
as the discriminator so Swift clients can branch on a value rather than on type
casts, and document the pattern in the iOS integration guide.

### 5.4 `namesTruncated`

Surfacing truncation explicitly is a deliberate choice: a client that pre-fills a
form must know the name may be incomplete rather than silently trusting it. This
is the passport analogue of 0.1.0's honesty about `bloodType == null`.

---

## 6. Capture — the hard part

The gate needs `Id3` accepted (a one-line change, thanks to 0.2.0's
parametrization). **The geometry is the easy part.** The real difficulties:

- **Curvature.** An open passport bows toward the spine — precisely where the MRZ
  sits, at the foot of the data page. OCR degrades on deformed text.
- **Laminate glare.** The data page is laminated and reflective.
- **Page vs. card.** An open passport may be detected as two rectangles.
- **Data-page guidance.** The user must present the *data page*, not the cover.
  UX, not computer vision.

**Reference-class warning.** Airport MRZ readers solve this with controlled
infrared illumination and physical flattening against glass. A handheld phone
camera has neither. Their reliability is not a fair benchmark for this SDK, and
should not be promised to clients.

**D12 — Passports are the likely trigger for the custom-model escalation.**
0.2.0's D9 defers a custom capture-quality model until the generic gate proves
insufficient. Cédulas are flat, matte, rigid cards; passports are curved, glossy,
and floppy. If the escalation ever fires, this is where. 0.3.0 should re-run
0.2.0's field evaluation (FN/FP rates) **specifically for `Id3`** rather than
assuming the `Id1` thresholds carry over.

---

## 7. Privacy

0.1.0's constraints hold unchanged: on-device only, no persistence, no
transmission, no PII logging.

Two additions specific to passports:

- **Passport data is more sensitive than a cédula** — it identifies a foreign
  national and reveals nationality and travel-document status. The no-persistence
  rule is not merely a design preference here.
- **Reading ≠ verifying.** 0.3.0 extracts what is printed. It does **not**
  establish that the passport is authentic or that the bearer is the holder.
  Clients must not treat a successful scan as identity verification. This belongs
  in the integration documentation, not just this file.

---

## 8. Phases

| # | Phase | Output |
|---|-------|--------|
| 1 | **TD3 parser** (`commonMain`) ✅ | `Td3MrzParser` pinned byte-for-byte to the public ICAO 9303 specimen; composite excludes nationality/sex; optional-data cd may be `'<'`; OCR repair never touches the alphanumeric passport/personal numbers. |
| 2 | **Data model migration** ✅ | `ScannedDocument` sealed hierarchy shipped; `DocumentSource` removed (redundant with `documentType`); facade keeps its name and gains explicit `parseMrzTd3`. |
| 3 | **`ScanMode.Passport` + gate** ✅ | `ScanMode.acceptedFormats` (ColombianId→Id1, Passport→Id3); Passport mode has no PDF417 leg; data-page instruction ES/EN. |
| 4 | **Field evaluation for `Id3`** | FN/FP rates under curvature and glare → threshold tuning → custom-model go/no-go (D12). Tooling (GateStats + diagnostics toggles) already shipped in 0.2.0. |

> Sequence rationale: Phase 1 is pure and carries no platform risk — same reason
> 0.1.0 built parsers before scanners. Phase 2 is the largest and riskiest piece
> (public API break), and is independent of capture work, so it should not wait
> on it.

---

## 9. Deferred

- **NFC / e-passport chip.** The chip (BAC/PACE) holds authoritative, digitally
  signed data plus a face image — strictly better than OCR, and the MRZ read by
  0.3.0 is exactly the key needed to unlock it. This is the natural successor
  version: 0.3.0 makes it possible.
- **Cédula de extranjería.** Separate document, separate format. If client demand
  is for *resident* foreigners rather than *visitors*, this may matter more than
  TD3 — worth re-checking against real client flows.
- **Custom capture-quality model.** Conditional on Phase 4 (D12).

---

## 10. Open items

- **`Id3` gate thresholds** — deliberately uncalibrated in 0.2.0. Set in Phase 4.
- **Custom-model bar** — the numeric FN/FP thresholds are still unset (carried
  from 0.2.0 §7); passports make this decision concrete rather than theoretical.
- **Version label** — 0.3.0 vs. 1.0.0, given the breaking model change (§5.2).
- **Migration guide** — 0.2.0 clients need a documented path from `IdCardData` to
  `ScannedDocument`.
- **Blood-type gap** — still open since 0.1.0. `ScannedDocument` partly resolves
  it structurally: `bloodType` now lives only on `ColombianId`, so passport
  clients never see the field at all.
