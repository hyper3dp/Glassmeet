import Supabase
import SwiftUI

struct SignInView: View {
    @Binding var path: [AuthRoute]
    @State private var email = ""
    @State private var password = ""
    @State private var busy: Busy?
    @State private var error: String?

    private enum Busy { case password, google, linkedIn }

    var body: some View {
        AuthPage {
            AuthHeader(title: "Sign in to GlassMeet", subtitle: "Schedule meetings. Stay on WhatsApp.")

            VStack(spacing: Theme.Space.m) {
                AuthField(title: "Email", text: $email, kind: .email)
                AuthField(title: "Password", text: $password, kind: .password)
                    .onSubmit { Task { await signIn() } }
                HStack {
                    Spacer()
                    Button("Forgot password?") { path.append(.reset(email: email.trimmingCharacters(in: .whitespaces))) }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.link)
                        .frame(minHeight: Theme.minTouch)
                }
                InlineError(text: error)
                AuthButton(title: "Sign in", busy: busy == .password) { Task { await signIn() } }
                    .disabled(busy != nil)
            }

            divider

            VStack(spacing: Theme.Space.s) {
                ProviderButton(title: "Continue with Google", logo: "GoogleLogo", busy: busy == .google) {
                    Task { await oauth(.google, .google) }
                }
                ProviderButton(title: "Continue with LinkedIn", logo: "LinkedInLogo", busy: busy == .linkedIn) {
                    Task { await oauth(.linkedinOIDC, .linkedIn) }
                }
            }
            .disabled(busy != nil)

            HStack(spacing: 4) {
                Text("New to GlassMeet?").foregroundStyle(Theme.textSecondary)
                Button("Create an account") { path.append(.createAccount) }
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.link)
            }
            .font(.subheadline)
            .frame(minHeight: Theme.minTouch)
        }
        .toolbar(.hidden, for: .navigationBar)
        .animation(.snappy, value: error)
        .sensoryFeedback(.error, trigger: error) { _, new in new != nil }
    }

    private var divider: some View {
        HStack(spacing: Theme.Space.s) {
            Rectangle().fill(Theme.border).frame(height: 1)
            Text("or").font(.footnote).foregroundStyle(Theme.textTertiary)
            Rectangle().fill(Theme.border).frame(height: 1)
        }
    }

    private func signIn() async {
        let address = email.trimmingCharacters(in: .whitespaces)
        guard AuthRules.validEmail(address) else { error = "Enter your email address."; return }
        guard !password.isEmpty else { error = "Enter your password."; return }
        busy = .password
        defer { busy = nil }
        do {
            try await AuthAPI.signIn(email: address, password: password)
            error = nil
        } catch {
            if AuthAPI.errorCode(error) == "email_not_confirmed" {
                self.error = nil
                try? await AuthAPI.resendSignUp(email: address)
                path.append(.verify(email: address))
            } else {
                self.error = AuthAPI.message(error)
            }
        }
    }

    private func oauth(_ provider: Provider, _ which: Busy) async {
        busy = which
        defer { busy = nil }
        do {
            try await AuthAPI.signIn(with: provider)
            error = nil
        } catch {
            if !AuthAPI.isCancel(error) { self.error = AuthAPI.message(error) }
        }
    }
}

/// An outlined capsule for a sign-in provider
private struct ProviderButton: View {
    var title: String
    /// The provider's own logo, from the asset catalog
    var logo: String
    var busy: Bool
    var action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    var body: some View {
        Button(action: action) {
            HStack(spacing: Theme.Space.s) {
                Image(logo).resizable().scaledToFit().frame(width: 22, height: 22)
                    .opacity(isEnabled ? 1 : 0.45)
                    .accessibilityHidden(true)
                Text(title).font(.headline)
                Spacer(minLength: 0)
                if busy { ProgressView() }
            }
            .foregroundStyle(Theme.text.opacity(isEnabled ? 1 : 0.45))
            .padding(.horizontal, Theme.Space.l)
            .frame(minHeight: 50)
            .background(Theme.surface, in: Capsule())
            .overlay(Capsule().strokeBorder(Theme.border, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
