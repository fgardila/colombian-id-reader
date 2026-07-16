import SwiftUI
import SharedLogic

struct ContentView: View {
    @State private var isScanning = false
    @State private var selectedFilter: DetectorFilter = .all
    @State private var diagnostics = false
    @State private var result: ScannedDocument?

    var body: some View {
        if let data = result {
            ResultView(data: data) { result = nil }
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

            Button("Escanear cédula") {
                selectedFilter = .all
                isScanning = true
            }
            .buttonStyle(.borderedProminent)

            HStack(spacing: 12) {
                Button("Solo PDF417") {
                    selectedFilter = .pdf417Only
                    isScanning = true
                }
                Button("Solo MRZ") {
                    selectedFilter = .mrzOnly
                    isScanning = true
                }
            }
            .buttonStyle(.bordered)

            Toggle("Diagnóstico en consola", isOn: $diagnostics)
                .frame(maxWidth: 260)
                .onChange(of: diagnostics) { _, enabled in
                    ScanDebug.shared.listener = enabled ? { print("ColombianIdScan: \($0)") } : nil
                }
        }
        .padding(32)
        .fullScreenCover(isPresented: $isScanning) {
            ScannerView(
                detectorFilter: selectedFilter,
                onResult: { data in
                    result = data
                    isScanning = false
                },
                onCancel: { isScanning = false }
            )
            .ignoresSafeArea()
        }
    }
}

struct ResultView: View {
    let data: ScannedDocument
    let onScanAgain: () -> Void

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

                Button("Escanear otra") { onScanAgain() }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 24)
            }
            .padding(24)
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
