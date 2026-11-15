import SwiftUI

/// Places the host meets guests; picked in the event type form for in-person events
struct AddressesView: View {
    @State private var addresses: [Address] = []
    @State private var state: LoadState = .idle
    @State private var label = ""
    @State private var address = ""
    @State private var adding = false
    @State private var error: String?

    var body: some View {
        List {
            Section {
                if addresses.isEmpty, state.isLoaded {
                    Text("No saved addresses yet.").font(.subheadline).foregroundStyle(Theme.textSecondary)
                }
                ForEach(addresses) { a in
                    VStack(alignment: .leading, spacing: 2) {
                        if let l = a.label, !l.isEmpty { Text(l).font(.headline).foregroundStyle(Theme.text) }
                        Text(a.address ?? "").font(.subheadline).foregroundStyle(Theme.textSecondary)
                    }
                    .swipeActions {
                        Button("Delete", systemImage: "trash", role: .destructive) { Task { await remove(a) } }
                    }
                }
            } footer: {
                Text("Swipe left to delete one. Event types that use it keep their address.")
            }
            .listRowBackground(Theme.surface)

            Section("Add an address") {
                TextField("Label, such as Office (optional)", text: $label)
                TextField("Street, building, city", text: $address, axis: .vertical).lineLimit(2...4)
                Button { Task { await add() } } label: {
                    HStack {
                        Label("Save address", systemImage: "plus.circle.fill")
                        if adding { Spacer(); ProgressView() }
                    }
                }
                .foregroundStyle(Theme.link)
                .disabled(address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || adding)
            }
            .listRowBackground(Theme.surface)
        }
        .themedList()
        .overlay {
            if !state.isLoaded {
                if let e = state.error { ErrorStateView(message: e) { await load() } } else { LoadingView() }
            }
        }
        .navigationTitle("Addresses")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
        .errorAlert($error)
    }

    private func load() async {
        if addresses.isEmpty { state = .loading }
        do {
            addresses = try await ProfileAPI.addresses()
            state = .loaded
        } catch where AppData.isCancel(error) {
        } catch {
            state = .failed(error.readable)
        }
    }

    private func add() async {
        adding = true
        defer { adding = false }
        do {
            let saved = try await ProfileAPI.addAddress(label: String(label.trimmingCharacters(in: .whitespaces).prefix(40)),
                                                        address: String(address.trimmingCharacters(in: .whitespacesAndNewlines).prefix(300)))
            addresses.append(saved)
            label = ""
            address = ""
        } catch {
            self.error = "Couldn’t save the address. \(error.readable)"
        }
    }

    private func remove(_ a: Address) async {
        do {
            try await ProfileAPI.deleteAddress(a.id)
            addresses.removeAll { $0.id == a.id }
        } catch {
            self.error = "Couldn’t delete the address. \(error.readable)"
        }
    }
}
