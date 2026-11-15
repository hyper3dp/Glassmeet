import SwiftUI
import UIKit

/// The design tokens from mobile/SPEC.md. Names match the Figma variables and Theme.kt.
enum Theme {
    // MARK: Colours (light / dark)

    static let background = dynamic(0xF6F9F9, 0x0F1516)
    static let surface = dynamic(0xFFFFFF, 0x162022)
    static let surfaceRaised = dynamic(0xEEF4F4, 0x1D2A2C)
    static let text = dynamic(0x344248, 0xE6EEEE)
    static let textSecondary = dynamic(0x55686D, 0xA0B4B4)
    static let textTertiary = dynamic(0x66787C, 0x7F9495)
    static let primary = dynamic(0x2B7A75, 0x66ACA9)
    static let onPrimary = dynamic(0xFFFFFF, 0x14201F)
    static let primarySoft = dynamic(0xE3F1F0, 0x1B3332)
    static let link = dynamic(0x24706B, 0x7FC4C0)
    static let border = dynamic(0xD6E2E1, 0x2A3A3C)
    /// The logo mark only, never text
    static let brandTeal = Color(uiColor: UIColor(hex: 0x66ACA9))
    static let success = dynamic(0x0A8A4C, 0x3FB97A)
    static let warning = dynamic(0xB25E00, 0xE3A044)
    static let danger = dynamic(0xC8372D, 0xFF7B72)
    /// Message previews only
    static let whatsappBubble = dynamic(0xD9FDD3, 0x144D37)

    /// An event type's own colour (event_types.color), or primary
    static func eventColor(_ hex: String?) -> Color {
        guard var value = hex?.trimmingCharacters(in: .whitespaces), !value.isEmpty else { return primary }
        if value.hasPrefix("#") { value.removeFirst() }
        if value.count == 3 { value = value.map { "\($0)\($0)" }.joined() }
        guard value.count == 6, let rgb = UInt32(value, radix: 16) else { return primary }
        return Color(uiColor: UIColor(hex: rgb))
    }

    // MARK: Spacing, shape, touch targets

    enum Space {
        static let xxs: CGFloat = 4
        static let xs: CGFloat = 8
        static let s: CGFloat = 12
        static let m: CGFloat = 16
        static let l: CGFloat = 20
        static let xl: CGFloat = 24
        static let xxl: CGFloat = 32
    }

    enum Radius {
        /// Cards: concentric with the iOS 26 display corners
        static let card: CGFloat = 26
        static let field: CGFloat = 12
        static let tile: CGFloat = 20
    }

    static let minTouch: CGFloat = 44

    // MARK: Type (SF Pro through Dynamic Type)

    enum Typography {
        static let largeTitle = Font.largeTitle.weight(.bold)
        static let title = Font.title.weight(.bold)
        static let title2 = Font.title2.weight(.bold)
        static let title3 = Font.title3.weight(.semibold)
        static let headline = Font.headline
        static let body = Font.body
        static let callout = Font.callout
        static let subheadline = Font.subheadline
        static let footnote = Font.footnote
        static let caption = Font.caption
        /// Numbers in stat tiles
        static let stat = Font.title.weight(.bold).monospacedDigit()
    }

    private static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

extension View {
    /// Inset-grouped list on the page background, with opaque surface rows
    func themedList() -> some View {
        listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
    }

    /// An opaque content card
    func card(padding: CGFloat = Theme.Space.m) -> some View {
        self.padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface, in: .rect(cornerRadius: Theme.Radius.card))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.card).strokeBorder(Theme.border.opacity(0.6), lineWidth: 0.5))
    }

    /// A text field on a raised fill (sign-in screens)
    func fieldBackground() -> some View {
        padding(.horizontal, Theme.Space.m)
            .frame(minHeight: 50)
            .background(Theme.surfaceRaised, in: .rect(cornerRadius: Theme.Radius.field))
    }
}
