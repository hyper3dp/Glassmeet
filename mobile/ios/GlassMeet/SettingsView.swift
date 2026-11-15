import SwiftUI

struct SettingsView: View {
    @Environment(Account.self) private var account
    @State private var name = ""
    @State private var phone = ""
    @State private var template = WhatsApp.confirmation
    @State private var loaded = false
    @State private var saved: Part?
    @State private var busy: Part?
    @State private var error: String?
    @State private var confirmSignOut = false

    private enum Part { case profile, whatsApp }

    var body: some View {
        Form {
            Section {
                HStack(spacing: 14) {
                    Avatar(name: account.hostName, size: 52)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(account.hostName).font(.headline)
                        Text(account.email ?? "").font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                TextField("Your name", text: $name).textContentType(.name)
                Button(saved == .profile ? "Saved" : "Save name") { Task { await saveName() } }
                    .disabled(busy != nil || name.trimmingCharacters(in: .whitespaces).isEmpty)
            } header: {
                Text("Profile")
            } footer: {
                Text("Guests see this on your booking page and in messages.")
            }

            Section {
                TextField("WhatsApp number, with country code", text: $phone)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                TextField("Confirmation message", text: $template, axis: .vertical)
                    .lineLimit(4...12)
                Button(saved == .whatsApp ? "Saved" : "Save WhatsApp settings") { Task { await saveWhatsApp() } }
                    .disabled(busy != nil)
            } header: {
                Text("WhatsApp")
            } footer: {
                Text("GlassMeet prepares the message and opens WhatsApp; you send it yourself. [Name], [Date] and [Time] are filled in for each meeting.")
            }

            Section {
                Link("Open GlassMeet on the web", destination: Backend.web("dashboard"))
                Link("Privacy Policy", destination: Backend.web("privacy"))
                Link("Terms of Service", destination: Backend.web("terms"))
            } header: {
                Text("More on the web")
            } footer: {
                Text("Calendar connections, analytics, holidays and embedding are managed in the web app.")
            }

            Section {
                Button("Sign out", role: .destructive) { confirmSignOut = true }
            }
        }
        .navigationTitle("Settings")
        .task { await load() }
        .errorAlert($error)
        .sensoryFeedback(.success, trigger: saved)
        .confirmationDialog("Sign out of GlassMeet?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { await account.signOut() } }
        }
    }

    private func load() async {
        guard !loaded else { return }
        name = account.profile?.fullName ?? account.hostName
        if let settings = try? await API.whatsApp() {
            phone = settings.phoneNumber ?? ""
            template = settings.confirmationTemplate ?? WhatsApp.confirmation
        }
        loaded = true
    }

    private func saveName() async {
        busy = .profile
        defer { busy = nil }
        do {
            try await API.updateName(name.trimmingCharacters(in: .whitespaces))
            await account.reloadProfile()
            saved = .profile
        } catch {
            self.error = "Your name could not be saved. Try again."
        }
    }

    private func saveWhatsApp() async {
        busy = .whatsApp
        defer { busy = nil }
        let p = phone.trimmingCharacters(in: .whitespaces)
        let t = template.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            try await API.saveWhatsApp(phone: p.isEmpty ? nil : p, template: t.isEmpty ? nil : t)
            saved = .whatsApp
        } catch {
            self.error = "Your WhatsApp settings could not be saved. Try again."
        }
    }
}
