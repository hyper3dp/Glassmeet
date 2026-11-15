import SwiftUI

/// Email -> code -> new password. Also opened from Settings (signed in), starting at the code.
struct ResetPasswordView: View {
    @Environment(Session.self) private var session
    @Environment(\.dismiss) private var dismiss
    @State private var email: String
    @State private var step: Step
    @State private var code = ""
    @State private var password = ""
    @State private var busy = false
    @State private var error: String?
    @State private var notice: String?
    var onDone: (() -> Void)?

    private enum Step { case email, code, password }

    init(email: String, codeAlreadySent: Bool = false, onDone: (() -> Void)? = nil) {
        _email = State(initialValue: email)
        _step = State(initialValue: codeAlreadySent ? .code : .email)
        self.onDone = onDone
    }

    var body: some View {
        AuthPage {
            switch step {
            case .email:
                AuthHeader(title: "Reset your password", subtitle: "We’ll email you a code to set a new one.")
                VStack(spacing: Theme.Space.m) {
                    AuthField(title: "Email", text: $email, kind: .email)
                    InlineError(text: error)
                    AuthButton(title: "Send code", busy: busy) { Task { await send() } }
                }
            case .code:
                AuthHeader(title: "Enter the code", subtitle: "We sent a code to \(email).")
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
                    AuthButton(title: "Continue", busy: busy) { Task { await verify() } }
                    Button("Send a new code") { Task { await send(again: true) } }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.link)
                        .frame(minHeight: Theme.minTouch)
                        .disabled(busy)
                }
            case .password:
                AuthHeader(title: "Choose a new password", subtitle: "Use at least 8 characters.")
                VStack(spacing: Theme.Space.m) {
                    AuthField(title: "New password", text: $password, kind: .newPassword)
                    InlineError(text: error)
                    AuthButton(title: "Save password", busy: busy) { Task { await save() } }
                }
            }
        }
        .navigationTitle("Reset password")
        .navigationBarTitleDisplayMode(.inline)
        .animation(.snappy, value: error)
        .onDisappear {
            // left halfway: whoever verified the code stays signed in
            session.holdSignedOut = false
        }
    }

    private func send(again: Bool = false) async {
        let address = email.trimmingCharacters(in: .whitespaces)
        guard AuthRules.validEmail(address) else { error = "Enter your email address."; return }
        busy = true
        defer { busy = false }
        do {
            try await AuthAPI.sendReset(email: address)
            email = address
            error = nil
            if again { notice = "A new code is on its way." }
            withAnimation { step = .code }
        } catch {
            self.error = AuthAPI.message(error)
        }
    }

    private func verify() async {
        guard AuthRules.validCode(code) else { error = "Enter the 6 to 10 digit code from the email."; return }
        busy = true
        defer { busy = false }
        // verifying the code signs in; keep this page up until the password is set
        if !session.signedIn { session.holdSignedOut = true }
        do {
            try await AuthAPI.verifyReset(email: email, code: code)
            error = nil
            withAnimation { step = .password }
        } catch {
            session.holdSignedOut = false
            self.error = AuthAPI.message(error)
        }
    }

    private func save() async {
        guard password.count >= 8 else { error = "Use at least 8 characters for your password."; return }
        busy = true
        defer { busy = false }
        do {
            try await AuthAPI.setPassword(password)
            error = nil
            session.holdSignedOut = false
            if let onDone { onDone() } else { dismiss() }
        } catch {
            self.error = AuthAPI.message(error)
        }
    }
}
