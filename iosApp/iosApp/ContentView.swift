import SwiftUI
import SharedLogic

/// Which cédula generation the demo user says they hold — drives the
/// ghost wireframe only; the scanner itself decides by evidence.
private enum DemoGeneration: String, CaseIterable, Identifiable {
    case amarilla = "Amarilla"
    case digital = "Digital"
    var id: String { rawValue }
}

struct ContentView: View {
    @State private var isScanning = false
    @State private var selectedMode: ScanMode = ScanModeColombianId.shared
    @State private var selectedFilter: DetectorFilter = .all
    @State private var captureImages = false
    @State private var generation: DemoGeneration = .digital
    @State private var scanPhase: CapturePhase = .back
    @State private var gateHint: GateHint?
    @State private var diagnostics = false
    @State private var capture: ScanCapture?

    var body: some View {
        if let capture {
            ResultView(capture: capture) {
                // Ciclo de vida §7: desechar las imágenes al salir.
                capture.images?.dispose()
                self.capture = nil
            }
        } else {
            home
        }
    }

    private var home: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.text.rectangle")
                .font(.system(size: 60))
                .foregroundColor(.accentColor)
            Text("colombian-id-reader")
                .font(.headline)
            Text("Demo")
                .font(.subheadline)
                .foregroundColor(.secondary)

            Picker("¿Cuál cédula vas a escanear?", selection: $generation) {
                ForEach(DemoGeneration.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 280)

            Button("Escanear cédula") {
                selectedMode = ScanModeColombianId.shared
                selectedFilter = .all
                startScan()
            }
            .buttonStyle(.borderedProminent)

            Button("Escanear pasaporte") {
                // Pasaporte: solo página de datos, sin captura (1.0.0 §3).
                selectedMode = ScanModePassport.shared
                selectedFilter = .all
                startScan()
            }
            .buttonStyle(.borderedProminent)

            HStack(spacing: 12) {
                Button("Solo PDF417") {
                    selectedMode = ScanModeColombianId.shared
                    selectedFilter = .pdf417Only
                    startScan()
                }
                Button("Solo MRZ") {
                    selectedMode = ScanModeColombianId.shared
                    selectedFilter = .mrzOnly
                    startScan()
                }
            }
            .buttonStyle(.bordered)

            Toggle("Capturar imágenes (frente y reverso)", isOn: $captureImages)
                .frame(maxWidth: 280)

            Toggle("Diagnóstico en consola", isOn: $diagnostics)
                .frame(maxWidth: 280)
                .onChange(of: diagnostics) { _, enabled in
                    ScanDebug.shared.listener = enabled ? { print("ColombianIdScan: \($0)") } : nil
                }
        }
        .padding(32)
        .fullScreenCover(isPresented: $isScanning) {
            ZStack {
                ScannerView(
                    mode: selectedMode,
                    detectorFilter: selectedFilter,
                    captureImages: selectedMode is ScanModeColombianId ? captureImages : false,
                    onGateHint: { gateHint = $0 },
                    onCapturePhase: { phase in
                        scanPhase = phase
                        gateHint = nil // fresh side, show the ghost again
                    },
                    onResult: { capture in
                        self.capture = capture
                        isScanning = false
                    },
                    onCancel: { isScanning = false }
                )
                .ignoresSafeArea()

                // Ghost guidance: wireframe of the side to present, in
                // the same card window the library overlay cuts (85%
                // width, ID-1 aspect, centered). Fades once the gate
                // sees a document.
                if selectedMode is ScanModeColombianId {
                    Image(ghostImageName)
                        .resizable()
                        .aspectRatio(85.6 / 54, contentMode: .fit)
                        .frame(maxWidth: UIScreen.main.bounds.width * 0.85)
                        .opacity(ghostOpacity)
                        .animation(.easeInOut(duration: 0.3), value: ghostOpacity)
                        .allowsHitTesting(false)
                }
            }
        }
    }

    private func startScan() {
        scanPhase = (selectedMode is ScanModeColombianId && captureImages) ? .front : .back
        gateHint = nil
        isScanning = true
    }

    private var ghostImageName: String {
        switch (generation, scanPhase) {
        case (.amarilla, .front): return "CedulaAmarillaFront"
        case (.amarilla, _): return "CedulaAmarillaBack"
        case (.digital, .front): return "CedulaDigitalFront"
        case (.digital, _): return "CedulaDigitalBack"
        }
    }

    private var ghostOpacity: Double {
        switch gateHint {
        case nil, GateHint.noDocument: return 0.45
        case GateHint.pass: return 0
        default: return 0.12
        }
    }
}

struct ResultView: View {
    let capture: ScanCapture
    let onScanAgain: () -> Void

    private var data: ScannedDocument { capture.document }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Documento leído")
                    .font(.title2.bold())
                    .padding(.bottom, 16)

                field("Tipo de documento", data.documentType.name)
                field("Nombres", data.givenNames)
                field("Apellidos", data.surnames)
                field("Fecha de nacimiento", data.birthDate?.description())
                field("Sexo", data.sex.name)
                // Sealed hierarchies export without exhaustive switch:
                // branch on the documentType discriminator, then cast.
                if let cedula = data as? ScannedDocumentColombianId {
                    field("NUIP", cedula.nuip)
                    field("Tipo de sangre (RH)", cedula.bloodType)
                    field("Vencimiento", cedula.expirationDate?.description())
                } else if let passport = data as? ScannedDocumentPassport {
                    field("Número de pasaporte", passport.passportNumber)
                    field("Estado emisor", passport.issuingState)
                    field("Nacionalidad", passport.nationality)
                    field("Vencimiento", passport.expirationDate.description())
                    field("Número personal", passport.personalNumber)
                    field("Nombre posiblemente truncado", passport.namesTruncated ? "Sí" : "No")
                }

                if let images = capture.images {
                    field("Verificación de nombres (frente vs. reverso)", nameMatchText)
                    if let front = DocumentImagesNSDataKt.frontData(images: images),
                       let image = UIImage(data: front) {
                        capturedImage("Frente", image)
                    }
                    if let back = UIImage(data: DocumentImagesNSDataKt.backData(images: images)) {
                        capturedImage("Reverso", back)
                    }
                }

                Button("Escanear otra") { onScanAgain() }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 24)
            }
            .padding(24)
        }
    }

    private var nameMatchText: String {
        switch capture.nameMatch {
        case .match: return "Coinciden"
        case .mismatch: return "NO coinciden"
        default: return "No verificado"
        }
    }

    private func capturedImage(_ label: String, _ image: UIImage) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity)
                .cornerRadius(8)
            Divider().padding(.vertical, 6)
        }
    }

    private func field(_ label: String, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value ?? "—")
                .font(.body)
            Divider().padding(.vertical, 6)
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
