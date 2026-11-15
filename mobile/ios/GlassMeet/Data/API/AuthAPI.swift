import AuthenticationServices
import Foundation
import Supabase

/// Sign in, create account, verify, reset password; errors in plain words.
enum AuthAPI {
    static func signIn(email: String, password: String) async throws {
        try await supabase.auth.signIn(email: email, password: password)
    }

    /// Google ("google") or LinkedIn ("linkedin_oidc"). supabase-swift opens an
    /// ASWebAuthenticationSession and reads the session from the glassmeet://auth-callback return.
    static func signIn(with provider: Provider) async throws {
        try await supabase.auth.signInWithOAuth(provider: provider, redirectTo: Backend.authCallback)
    }

    enum SignUpResult { case signedIn, verify, alreadyExists }

    static func signUp(name: String, email: String, password: String) async throws -> SignUpResult {
        let response = try await supabase.auth.signUp(email: email, password: password, data: ["full_name": .string(name)])
        if response.session != nil { return .signedIn }
        // an existing address gets a user with no identities (and no email is sent)
        if response.user.identities?.isEmpty == true { return .alreadyExists }
        return .verify
    }

    static func verifySignUp(email: String, code: String) async throws {
        try await supabase.auth.verifyOTP(email: email, token: code, type: .signup)
    }

    static func resendSignUp(email: String) async throws {
        try await supabase.auth.resend(email: email, type: .signup)
    }

    static func sendReset(email: String) async throws {
        try await supabase.auth.resetPasswordForEmail(email)
    }

    static func verifyReset(email: String, code: String) async throws {
        try await supabase.auth.verifyOTP(email: email, token: code, type: .recovery)
    }

    static func setPassword(_ password: String) async throws {
        try await supabase.auth.update(user: UserAttributes(password: password))
    }

    static func signOut() async {
        do { try await supabase.auth.signOut() } catch { try? await supabase.auth.signOut(scope: .local) }
    }

    /// true when the person closed the sign-in sheet themselves
    static func isCancel(_ error: Error) -> Bool {
        if let web = error as? ASWebAuthenticationSessionError, web.code == .canceledLogin { return true }
        return (error as NSError).domain == ASWebAuthenticationSessionErrorDomain && (error as NSError).code == ASWebAuthenticationSessionError.canceledLogin.rawValue
    }

    static func errorCode(_ error: Error) -> String? {
        (error as? AuthError)?.errorCode.rawValue
    }

    /// What to say under the button
    static func message(_ error: Error) -> String {
        if let url = error as? URLError {
            _ = url
            return "Couldn’t reach GlassMeet. Check your connection and try again."
        }
        switch errorCode(error) {
        case "invalid_credentials": return "That email and password don’t match. Check them and try again."
        case "email_not_confirmed": return "Confirm your email first. Enter the code we sent you."
        case "user_already_exists": return "An account with this email already exists. Sign in instead."
        case "weak_password": return "Choose a stronger password: at least 8 characters."
        case "otp_expired": return "That code has expired or isn’t right. Ask for a new one."
        case "over_email_send_rate_limit", "over_request_rate_limit": return "Too many tries. Wait a minute and try again."
        case "email_address_invalid", "validation_failed": return "Enter a valid email address."
        case "same_password": return "Choose a password you haven’t used here before."
        default: return error.readable
        }
    }
}
