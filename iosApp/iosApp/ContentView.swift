import SwiftUI
import SharedLogic

struct ContentView: View {
    @State private var isScanning = false
    @State private var selectedMode: ScanMode = .auto_
    @State private var result: IdCardData?

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
                selectedMode = .auto_
                isScanning = true
            }
            .buttonStyle(.borderedProminent)

            HStack(spacing: 12) {
                Button("Solo PDF417") {
                    selectedMode = .pdf417Only
                    isScanning = true
                }
                Button("Solo MRZ") {
                    selectedMode = .mrzOnly
                    isScanning = true
                }
            }
            .buttonStyle(.bordered)
        }
        .padding(32)
        .fullScreenCover(isPresented: $isScanning) {
            ScannerView(
                mode: selectedMode,
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
    let data: IdCardData
    let onScanAgain: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Documento leído")
                    .font(.title2.bold())
                    .padding(.bottom, 16)

                field("Número de documento", data.documentNumber)
                field("Primer nombre", data.firstName)
                field("Segundo nombre", data.secondName)
                field("Primer apellido", data.firstSurname)
                field("Segundo apellido", data.secondSurname)
                field("Fecha de nacimiento", data.birthDate?.description())
                field("Sexo", data.sex.name)
                field("Tipo de sangre (RH)", data.bloodType)
                field("Vencimiento", data.expirationDate?.description())
                field("Fuente", data.source.name)

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
