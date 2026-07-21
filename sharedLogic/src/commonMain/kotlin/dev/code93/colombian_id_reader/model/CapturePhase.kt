package dev.code93.colombian_id_reader.model

/**
 * Side being captured in the two-side flow (ARCHITECTURE-1.0.0.md §5),
 * surfaced to clients with their own guidance UI — the public twin of
 * the internal flow state, in the same spirit as [GateHint].
 *
 * The flow starts at [FRONT] when image capture is requested (cédula
 * modes), otherwise at [BACK] — the side that carries the data. The
 * phase callback fires on the FRONT → BACK transition.
 */
enum class CapturePhase { FRONT, BACK }
