import SwiftUI

/// Initials on primarySoft, or the photo when there is one
struct Avatar: View {
    var name: String
    var photo: URL? = nil
    var size: CGFloat = 40

    var body: some View {
        ZStack {
            Circle().fill(Theme.primarySoft)
            Text(Fmt.initials(name))
                .font(.system(size: size * 0.38, weight: .semibold, design: .rounded))
                .foregroundStyle(Theme.link)
            if let photo {
                AsyncImage(url: photo) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    }
                }
                .clipShape(Circle())
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// The event's colour, as a small dot beside its name
struct EventDot: View {
    var color: Color
    var size: CGFloat = 10
    var body: some View {
        Circle().fill(color).frame(width: size, height: size).accessibilityHidden(true)
    }
}

/// Request / Cancelled / Declined / Confirmed
struct StatusChip: View {
    var status: BookingStatus

    var body: some View {
        Text(status.label)
            .font(.caption.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, Theme.Space.xs)
            .padding(.vertical, 3)
            .background(color.opacity(0.12), in: Capsule())
    }

    private var color: Color {
        switch status {
        case .request: Theme.warning
        case .cancelled, .declined: Theme.danger
        case .finished: Theme.textSecondary
        case .confirmed: Theme.success
        }
    }
}

/// A rounded square icon, as in Settings rows
struct IconTile: View {
    var systemImage: String
    var color: Color = Theme.primary
    var size: CGFloat = 30
    /// onPrimary on the primary token (dark text in dark mode); pass .white on fixed brand colours
    var glyph: Color = Theme.onPrimary

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: size * 0.5, weight: .semibold))
            .foregroundStyle(glyph)
            .frame(width: size, height: size)
            .background(color, in: .rect(cornerRadius: size * 0.26))
            .accessibilityHidden(true)
    }
}

/// A row label with a tinted icon tile, for Settings-style lists
struct SettingsLabel: View {
    var title: String
    var systemImage: String
    var color: Color = Theme.primary
    var glyph: Color = Theme.onPrimary

    var body: some View {
        Label {
            Text(title).foregroundStyle(Theme.text)
        } icon: {
            IconTile(systemImage: systemImage, color: color, glyph: glyph)
        }
    }
}

/// A WhatsApp-style message bubble for previews
struct MessageBubble: View {
    var text: String

    var body: some View {
        Text(text.isEmpty ? " " : text)
            .font(.subheadline)
            .foregroundStyle(Theme.text)
            .textSelection(.enabled)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.whatsappBubble, in: UnevenRoundedRectangle(topLeadingRadius: 16, bottomLeadingRadius: 16, bottomTrailingRadius: 16, topTrailingRadius: 4))
    }
}

/// Wraps chips onto as many lines as they need
struct FlowLayout: Layout {
    var spacing: CGFloat = Theme.Space.xs

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, widest: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                y += rowHeight + spacing
                x = 0
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: min(widest, width), height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                y += rowHeight + spacing
                x = bounds.minX
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

/// A capsule chip that can be selected (durations, weekdays, placeholders)
struct ChoiceChip: View {
    var title: String
    var selected: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(selected ? Theme.link : Theme.text)
                .padding(.horizontal, 14)
                .frame(minHeight: 36)
                .background(selected ? Theme.primarySoft : Theme.surfaceRaised, in: Capsule())
                .overlay(Capsule().strokeBorder(selected ? Theme.primary : .clear, lineWidth: 1))
                // 36 pt to look at, 44 pt to tap
                .frame(minHeight: Theme.minTouch)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
