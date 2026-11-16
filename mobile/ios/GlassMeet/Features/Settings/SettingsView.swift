import SwiftUI
import UIKit

struct SettingsView: View {
    @Environment(Session.self) private var session
    @State private var confirmSignOut = false
    @State private var deleting = false
    @State private var resetting = false
    @State private var sendingReset = false
    @State private var error: String?
    @State private var toast: String?

    var body: some View {
        List {
            Section {
                NavigationLink(value: Route.profile) {
                    HStack(spacing: Theme.Space.m) {
                        Avatar(name: session.hostName, photo: session.profile?.photoURL, size: 60)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(session.hostName.isEmpty ? "Your profile" : session.hostName)
                                .font(Theme.Typography.title3)
                                .foregroundStyle(Theme.text)
                            Text(session.email ?? "")
                                .font(.subheadline)
                                .foregroundStyle(Theme.textSecondary)
                                .lineLimit(1)
                            Text("Photo, name, time zone and more")
                                .font(.footnote)
                                .foregroundStyle(Theme.link)
                        }
                    }
                    .padding(.vertical, Theme.Space.xxs)
                }
            }
            .listRowBackground(Theme.surface)

            Section {
                NavigationLink(value: Route.whatsApp) {
                    SettingsLabel(title: "WhatsApp messages", systemImage: "message.fill", color: Color(uiColor: UIColor(hex: 0x25D366)), glyph: .white)
                }
                NavigationLink(value: Route.calendarConnections) {
                    SettingsLabel(title: "Calendar connections", systemImage: "calendar", color: Theme.primary)
                }
                NavigationLink(value: Route.addresses) {
                    SettingsLabel(title: "Addresses", systemImage: "mappin.and.ellipse", color: Color(uiColor: UIColor(hex: 0xF29D38)), glyph: .white)
                }
            }
            .listRowBackground(Theme.surface)

            Section {
                Button { Task { await sendReset() } } label: {
                    HStack {
                        SettingsLabel(title: "Reset password", systemImage: "key.fill", color: Color(uiColor: UIColor(hex: 0x8E8E93)), glyph: .white)
                        Spacer()
                        if sendingReset { ProgressView() }
                    }
                }
                .disabled(sendingReset || session.email == nil)
            } footer: {
                Text("We’ll email you a code to set a new password. This also works if you signed up with Google or LinkedIn.")
            }
            .listRowBackground(Theme.surface)

            Section {
                Link(destination: Backend.web("dashboard")) {
                    SettingsLabel(title: "Open GlassMeet on the web", systemImage: "safari.fill", color: Color(uiColor: UIColor(hex: 0x0A84FF)), glyph: .white)
                }
                Link(destination: Backend.web("privacy")) {
                    SettingsLabel(title: "Privacy Policy", systemImage: "hand.raised.fill", color: Color(uiColor: UIColor(hex: 0x5E5CE6)), glyph: .white)
                }
                Link(destination: Backend.web("terms")) {
                    SettingsLabel(title: "Terms of Service", systemImage: "doc.text.fill", color: Color(uiColor: UIColor(hex: 0x8E8E93)), glyph: .white)
                }
            }
            .listRowBackground(Theme.surface)

            Section {
                Button("Sign out", role: .destructive) { confirmSignOut = true }
                    .frame(maxWidth: .infinity)
            }
            .listRowBackground(Theme.surface)

            Section {
                Button("Delete account", role: .destructive) { deleting = true }
                    .frame(maxWidth: .infinity)
            } footer: {
                Text("Permanently deletes your account and everything in it.")
            }
            .listRowBackground(Theme.surface)

            Section {} footer: {
                Text("GlassMeet \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")")
                    .frame(maxWidth: .infinity)
            }
        }
        .themedList()
        .navigationTitle("Settings")
        .confirmationDialog("Sign out of GlassMeet?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { Task { await session.signOut() } }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $deleting) { DeleteAccountSheet() }
        .sheet(isPresented: $resetting) {
            NavigationStack {
                ResetPasswordView(email: session.email ?? "", codeAlreadySent: true) {
                    resetting = false
                    toast = "Password changed"
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel", systemImage: "xmark") { resetting = false }
                    }
                }
            }
        }
        .errorAlert($error)
        .toast($toast)
        .task { await session.reloadProfile() }
    }

    private func sendReset() async {
        guard let email = session.email else { return }
        sendingReset = true
        defer { sendingReset = false }
        do {
            try await AuthAPI.sendReset(email: email)
            resetting = true
        } catch {
            self.error = AuthAPI.message(error)
        }
    }
}

/// Type DELETE to confirm; then calendars are disconnected, the account and its data deleted,
/// and the app signs out.
private struct DeleteAccountSheet: View {
    @Environment(Session.self) private var session
    @Environment(\.dismiss) private var dismiss
    @State private var typed = ""
    @State private var busy = false
    @State private var error: String?

    private var confirmed: Bool { typed.trimmingCharacters(in: .whitespaces).uppercased() == "DELETE" }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: Theme.Space.xs) {
                        Text("This permanently deletes \(session.email ?? "your account") and all its data:")
                            .foregroundStyle(Theme.text)
                        ForEach([
                            "your event types; their booking links stop working",
                            "your hours, days off and holiday settings",
                            "every meeting booked with you, with your guests’ details",
                            "templates, addresses, WhatsApp settings and your photo",
                            "your calendar link, which stops updating",
                        ], id: \.self) { line in
                            Label(line, systemImage: "minus").font(.subheadline).foregroundStyle(Theme.textSecondary)
                        }
                        Text("Guests aren’t told. Cancel or message anyone with an upcoming meeting first.")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.warning)
                            .padding(.top, Theme.Space.xxs)
                    }
                    .padding(.vertical, Theme.Space.xxs)
                }
                .listRowBackground(Theme.surface)
                Section {
                    TextField("DELETE", text: $typed)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .disabled(busy)
                } header: {
                    Text("Type DELETE to confirm")
                } footer: {
                    if let error { Text(error).foregroundStyle(Theme.danger) }
                }
                .listRowBackground(Theme.surface)
                Section {
                    Button(role: .destructive) { Task { await delete() } } label: {
                        HStack {
                            Spacer()
                            if busy { ProgressView() } else { Text("Delete everything").fontWeight(.semibold) }
                            Spacer()
                        }
                    }
                    .disabled(!confirmed || busy)
                }
                .listRowBackground(Theme.surface)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Delete account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Keep my account", systemImage: "xmark") { dismiss() }.disabled(busy)
                }
            }
        }
        .interactiveDismissDisabled(busy)
    }

    private func delete() async {
        busy = true
        defer { busy = false }
        do {
            try await ProfileAPI.deleteAccount()
        } catch {
            self.error = error.readable
        }
    }
}
