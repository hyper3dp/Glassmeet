import SwiftUI

struct CreateAccountView: View {
    @Binding var path: [AuthRoute]
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        AuthPage {
            AuthHeader(title: "Create your account", subtitle: "Share a link, get booked, confirm on WhatsApp.")
            VStack(spacing: Theme.Space.m) {
                AuthField(title: "Name", text: $name, kind: .name)
                AuthField(title: "Email", text: $email, kind: .email)
                AuthField(title: "Password", text: $password, kind: .newPassword)
                Text("At least 8 characters.")
                    .font(.footnote)
                    .foregroundStyle(Theme.textTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                InlineError(text: error)
                AuthButton(title: "Create account", busy: busy) { Task { await create() } }
            }
            Text("We’ll email you a code to confirm your address.")
                .font(.footnote)
                .foregroundStyle(Theme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .navigationBarTitleDisplayMode(.inline)
        .animation(.snappy, value: error)
    }

    private func create() async {
        let n = name.trimmingCharacters(in: .whitespaces)
        let e = email.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty else { error = "Enter your name."; return }
        guard AuthRules.validEmail(e) else { error = "Enter a valid email address."; return }
        guard password.count >= 8 else { error = "Use at least 8 characters for your password."; return }
        busy = true
        defer { busy = false }
        do {
            switch try await AuthAPI.signUp(name: n, email: e, password: password) {
            case .signedIn: error = nil
            case .verify: error = nil; path.append(.verify(email: e))
            case .alreadyExists: error = "An account with this email already exists. Sign in instead."
            }
        } catch {
            self.error = AuthAPI.message(error)
        }
    }
}

/// The 6 to 10 digit code from the confirmation email
struct VerifyCodeView: View {
    var email: String
    @State private var code = ""
    @State private var busy = false
    @State private var resending = false
    @State private var error: String?
    @State private var notice: String?

    var body: some View {
        AuthPage {
            AuthHeader(title: "Check your email", subtitle: "Enter the code we sent to \(email).")
            VStack(spacing: Theme.Space.m) {
                AuthField(title: "Code", text: $code, kind: .code)
                    .onChange(of: code) { _, new in
                        let digits = String(new.filter(\.isNumber).prefix(10))
                        if digits != new { code = digits }
                    }
                InlineError(text: error)
                if let notice {
                    Text(notice).font(.subheadline).foregroundStyle(Theme.success)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                AuthButton(title: "Verify", busy: busy) { Task { await verify() } }
                Button(resending ? "Sending…" : "Send a new code") { Task { await resend() } }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.link)
                    .frame(minHeight: Theme.minTouch)
                    .disabled(resending)
            }
            Text("Can’t find it? Check your spam folder.")
                .font(.footnote)
                .foregroundStyle(Theme.textSecondary)
        }
        .navigationBarTitleDisplayMode(.inline)
        .animation(.snappy, value: error)
    }

    private func verify() async {
        guard AuthRules.validCode(code) else { error = "Enter the 6 to 10 digit code from the email."; return }
        busy = true
        defer { busy = false }
        do {
            try await AuthAPI.verifySignUp(email: email, code: code)
            error = nil
        } catch {
            self.error = AuthAPI.message(error)
        }
    }

    private func resend() async {
        resending = true
        defer { resending = false }
        do {
            try await AuthAPI.resendSignUp(email: email)
            error = nil
            notice = "A new code is on its way."
        } catch {
            self.error = AuthAPI.message(error)
        }
    }
}
