import SafariServices
import SwiftUI

/// A web page shown inside the app (Safari's own view), so the host doesn't leave GlassMeet
struct SafariView: UIViewControllerRepresentable {
    var url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.preferredControlTintColor = UIColor(Theme.primary)
        controller.dismissButtonStyle = .done
        return controller
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

private struct WebPage: Identifiable {
    var url: URL
    var id: String { url.absoluteString }
}

extension View {
    /// Opens the page in an in-app browser sheet while [url] is set
    func inAppBrowser(_ url: Binding<URL?>) -> some View {
        sheet(item: Binding(get: { url.wrappedValue.map(WebPage.init) }, set: { url.wrappedValue = $0?.url })) { page in
            SafariView(url: page.url).ignoresSafeArea()
        }
    }
}
