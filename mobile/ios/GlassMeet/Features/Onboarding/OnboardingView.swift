import Supabase
import SwiftUI
import UIKit

/// First run after signing up: what GlassMeet does, the host's name and WhatsApp number, their
/// weekly hours, a first event type, and its link to share. Every step is needed, so there is no
/// Skip. Shown once per account on this phone.
struct OnboardingView: View {
    var finish: () -> Void
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data

    private enum Step: Int, CaseIterable { case welcome, aboutYou, hours, firstEvent, done }

    @State private var step: Step = .welcome
    @State private var name = ""
    @State private var whatsApp = ""
    @State private var eventName = "30 minute meeting"
    @State private var duration = 30
    @State private var location = "WhatsApp call"
    /// Weekly hours: the days (0 = Sunday) and one time range for all of them, Monday to Friday 9 to 5 to start
    @State private var days: Set<Int> = [1, 2, 3, 4, 5]
    @State private var from = Self.time(9 * 60)
    @State private var to = Self.time(17 * 60)
    @State private var created: EventType?
    @State private var busy = false
    @State private var error: String?
    @State private var toast: String?
    @FocusState private var focused: Bool

    private static let locations = ["WhatsApp call", "Google Meet", "Phone call", "Zoom"]

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ScrollView {
                Group {
                    switch step {
                    case .welcome: welcome
                    case .aboutYou: aboutYou
                    case .hours: hours
                    case .firstEvent: firstEvent
                    case .done: done
                    }
                }
                .padding(.horizontal, Theme.Space.xl)
                .padding(.top, Theme.Space.l)
                .padding(.bottom, Theme.Space.xxl)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
                .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                        removal: .move(edge: .leading).combined(with: .opacity)))
                .id(step)
            }
            .scrollDismissesKeyboard(.interactively)
            bottomBar
        }
        .background(Theme.background.ignoresSafeArea())
        .animation(.smooth, value: step)
        .errorAlert($error)
        .toast($toast)
        .onAppear {
            name = session.profile?.fullName ?? session.hostName
            whatsApp = session.profile?.whatsappNumber ?? ""
        }
        .interactiveDismissDisabled()
    }

    // MARK: Bars

    private var topBar: some View {
        HStack {
            if step != .welcome, step != .done {
                Button { go(back: true) } label: { Image(systemName: "chevron.left").font(.headline) }
                    .frame(width: Theme.minTouch, height: Theme.minTouch)
                    .accessibilityLabel("Back")
            } else {
                Color.clear.frame(width: Theme.minTouch, height: Theme.minTouch)
            }
            Spacer()
            // progress dots
            HStack(spacing: 6) {
                ForEach(Step.allCases, id: \.self) { s in
                    Capsule()
                        .fill(s.rawValue <= step.rawValue ? Theme.primary : Theme.border)
                        .frame(width: s == step ? 20 : 8, height: 8)
                }
            }
            .accessibilityElement()
            .accessibilityLabel("Step \(step.rawValue + 1) of \(Step.allCases.count)")
            Spacer()
            Color.clear.frame(width: Theme.minTouch, height: Theme.minTouch)
        }
        .padding(.horizontal, Theme.Space.s)
        .tint(Theme.link)
    }

    private var bottomBar: some View {
        VStack(spacing: Theme.Space.xs) {
            Button { Task { await next() } } label: {
                HStack(spacing: Theme.Space.xs) {
                    if busy { ProgressView().tint(Theme.onPrimary) }
                    Text(primaryTitle)
                }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(busy || !canContinue)
        }
        .padding(.horizontal, Theme.Space.xl)
        .padding(.top, Theme.Space.s)
        .padding(.bottom, Theme.Space.s)
        .background(Theme.background)
    }

    private var primaryTitle: String {
        switch step {
        case .welcome: "Get started"
        case .aboutYou, .hours: "Continue"
        case .firstEvent: "Create event type"
        case .done: "Go to Home"
        }
    }

    private var canContinue: Bool {
        switch step {
        case .aboutYou: !name.trimmingCharacters(in: .whitespaces).isEmpty
        case .hours: !days.isEmpty && Self.minutes(to) > Self.minutes(from)
        case .firstEvent: !eventName.trimmingCharacters(in: .whitespaces).isEmpty
        default: true
        }
    }

    // MARK: Steps

    private var welcome: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xl) {
            IconTile(systemImage: "calendar.badge.clock", color: Theme.primary, size: 72)
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text("Welcome to GlassMeet").font(Theme.Typography.largeTitle).foregroundStyle(Theme.text)
                Text("Guests book a time with you, and the meeting is confirmed on WhatsApp.")
                    .font(.title3).foregroundStyle(Theme.textSecondary)
            }
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                feature("link", "Share one link", "Send your booking link in a chat, an email or your bio.")
                feature("hand.tap", "Guests pick a time", "They see only the times you’re free, in their own time zone.")
                feature("bubble.left.and.text.bubble.right", "Confirm on WhatsApp", "Send the confirmation from your own WhatsApp with one tap.")
            }
            .padding(.top, Theme.Space.xs)
        }
    }

    private func feature(_ icon: String, _ title: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: Theme.Space.m) {
            Image(systemName: icon)
                .font(.title3.weight(.semibold))
                .foregroundStyle(Theme.link)
                .frame(width: 44, height: 44)
                .background(Theme.primarySoft, in: .circle)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline).foregroundStyle(Theme.text)
                Text(text).font(.subheadline).foregroundStyle(Theme.textSecondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var aboutYou: some View {
        VStack(alignment: .leading, spacing: Theme.Space.l) {
            heading("About you", "Guests see your name on your booking page.")
            field("Your name") {
                TextField("Name", text: $name)
                    .textContentType(.name)
                    .maxLength($name, 80)
                    .focused($focused)
            }
            field("WhatsApp number") {
                TextField("+971 50 123 4567", text: $whatsApp)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .maxLength($whatsApp, 24)
            }
            Text("Optional. Guests can message you on it after booking. Include the country code.")
                .font(.footnote).foregroundStyle(Theme.textSecondary)
            HStack(spacing: Theme.Space.s) {
                Image(systemName: "globe").foregroundStyle(Theme.link)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Time zone").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
                    Text(zoneName).font(.subheadline).foregroundStyle(Theme.textSecondary)
                }
            }
            .card()
        }
    }

    private static let dayLetters = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]

    private var hours: some View {
        VStack(alignment: .leading, spacing: Theme.Space.l) {
            heading("Your hours", "Guests can only book you on these days and times. You can add breaks, days off and different hours per day later in Availability.")
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text("Days").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
                // the week starts on the phone's first weekday
                let first = Calendar.current.firstWeekday - 1
                FlowLayout(spacing: 6) {
                    ForEach(0..<7, id: \.self) { i in
                        let day = (first + i) % 7
                        ChoiceChip(title: Self.dayLetters[day], selected: days.contains(day)) {
                            if days.contains(day) { days.remove(day) } else { days.insert(day) }
                        }
                    }
                }
            }
            VStack(spacing: 0) {
                DatePicker("From", selection: $from, displayedComponents: .hourAndMinute)
                    .frame(minHeight: Theme.minTouch)
                Divider()
                DatePicker("To", selection: $to, displayedComponents: .hourAndMinute)
                    .frame(minHeight: Theme.minTouch)
            }
            .foregroundStyle(Theme.text)
            .card(padding: Theme.Space.s)
            if days.isEmpty {
                InlineError(text: "Pick at least one day.")
            } else if Self.minutes(to) <= Self.minutes(from) {
                InlineError(text: "The end time must be after the start time.")
            }
        }
    }

    private static func time(_ minutes: Int) -> Date {
        Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: .now) ?? .now
    }

    private static func minutes(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private var zoneName: String {
        let id = session.profile?.timezone.flatMap { $0.isEmpty ? nil : $0 } ?? TimeZone.current.identifier
        return id.replacingOccurrences(of: "_", with: " ")
    }

    private var firstEvent: some View {
        VStack(alignment: .leading, spacing: Theme.Space.l) {
            heading("Your first event type", "An event type is a meeting guests can book. You can change everything later.")
            field("Name") {
                TextField("30 minute meeting", text: $eventName)
                    .maxLength($eventName, 80)
            }
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text("How long").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
                Picker("How long", selection: $duration) {
                    ForEach([15, 30, 45, 60], id: \.self) { Text("\($0) min").tag($0) }
                }
                .pickerStyle(.segmented)
                .onChange(of: duration) { old, new in
                    if eventName == "\(old) minute meeting" { eventName = "\(new) minute meeting" }
                }
            }
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text("Where").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
                VStack(spacing: 0) {
                    ForEach(Self.locations, id: \.self) { place in
                        Button { location = place } label: {
                            HStack {
                                Text(place).foregroundStyle(Theme.text)
                                Spacer()
                                if location == place { Image(systemName: "checkmark").foregroundStyle(Theme.primary).fontWeight(.semibold) }
                            }
                            .frame(minHeight: Theme.minTouch)
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                        if place != Self.locations.last { Divider() }
                    }
                }
                .card(padding: Theme.Space.s)
            }
        }
    }

    private var done: some View {
        VStack(alignment: .leading, spacing: Theme.Space.l) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(Theme.success)
            heading("You’re ready", created == nil
                ? "Share a booking link from Scheduling whenever you like."
                : "Share this link and guests can book you straight away.")
            if let created {
                let url = Backend.bookingURL(created.id)
                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text(created.title).font(.headline).foregroundStyle(Theme.text)
                    Text(url.absoluteString).font(.footnote).foregroundStyle(Theme.link).lineLimit(1).truncationMode(.middle)
                    HStack(spacing: Theme.Space.s) {
                        Button {
                            UIPasteboard.general.url = url
                            toast = "Link copied"
                        } label: { Label("Copy", systemImage: "doc.on.doc") }
                            .buttonStyle(SecondaryButtonStyle(compact: true))
                        ShareLink(item: url, message: Text("Book a time with me: \(url.absoluteString)")) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(SecondaryButtonStyle(compact: true))
                    }
                }
                .card()
            }
            VStack(alignment: .leading, spacing: Theme.Space.s) {
                tip("plus.circle", "Tap + in Scheduling to add more event types.")
                tip("bell", "New bookings show up in Booking messages on Home.")
                tip("calendar.badge.checkmark", "Connect your calendar in Settings so busy times stay off your page.")
            }
        }
    }

    private func tip(_ icon: String, _ text: String) -> some View {
        Label { Text(text).font(.subheadline).foregroundStyle(Theme.textSecondary) } icon: {
            Image(systemName: icon).foregroundStyle(Theme.link)
        }
    }

    private func heading(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Text(title).font(Theme.Typography.title).foregroundStyle(Theme.text)
            Text(text).font(.body).foregroundStyle(Theme.textSecondary)
        }
    }

    private func field<Content: View>(_ label: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Text(label).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
            content().fieldBackground()
        }
    }

    // MARK: Actions

    private func go(back: Bool = false) {
        focused = false
        var target = Step(rawValue: step.rawValue + (back ? -1 : 1)) ?? .done
        // a host who already has event types doesn't need a first one
        if target == .firstEvent, !data.eventTypes.isEmpty { target = back ? .hours : .done }
        step = target
    }

    private func next() async {
        switch step {
        case .welcome:
            go()
        case .aboutYou:
            if await saveProfile() { go() }
        case .hours:
            if await saveHours() { go() }
        case .firstEvent:
            if await createEvent() { go() }
        case .done:
            finish()
        }
    }

    private func saveProfile() async -> Bool {
        let cleanName = name.trimmingCharacters(in: .whitespaces)
        let number = whatsApp.filter { $0.isNumber || $0 == "+" }
        if !number.isEmpty, number.filter(\.isNumber).count < 7 {
            error = "Enter the full WhatsApp number with its country code, or leave it empty."
            return false
        }
        busy = true
        defer { busy = false }
        var fields: Row = ["full_name": .string(cleanName)]
        if !number.isEmpty { fields["whatsapp_number"] = .string(number.hasPrefix("+") ? number : "+\(number)") }
        if (session.profile?.timezone ?? "").isEmpty { fields["timezone"] = .string(TimeZone.current.identifier) }
        do {
            let saved = try await ProfileAPI.update(fields)
            session.profile = saved
            try? await ProfileAPI.syncHostFields(name: saved.fullName, photo: saved.profilePhoto, timezone: saved.timezone)
            return true
        } catch {
            self.error = "Your details weren’t saved. \(error.readable)"
            return false
        }
    }

    /// Replaces the weekly hours with the chosen days and times
    private func saveHours() async -> Bool {
        busy = true
        defer { busy = false }
        let start = Self.minutes(from), end = Self.minutes(to)
        do {
            for rule in try await AvailabilityAPI.rules() { try await AvailabilityAPI.deleteRule(rule.id) }
            for day in days.sorted() { _ = try await AvailabilityAPI.insertRule(day: day, start: start, end: end) }
            return true
        } catch {
            self.error = "Your hours weren’t saved. \(error.readable)"
            return false
        }
    }

    private func createEvent() async -> Bool {
        busy = true
        defer { busy = false }
        var draft = EventTypeDraft()
        draft.name = eventName
        draft.duration = duration
        draft.location = location
        draft.noticeValue = 30
        do {
            let saved = try await EventTypesAPI.save(nil, fields: draft.fields(), profile: session.profile)
            data.update(saved)
            created = saved
            return true
        } catch {
            self.error = "The event type wasn’t created. \(error.readable)"
            return false
        }
    }
}

/// Whether this account has seen onboarding on this phone
enum Onboarding {
    private static func key(_ id: UUID) -> String { "onboarded-\(id.uuidString.lowercased())" }
    static func isDone(_ id: UUID) -> Bool { UserDefaults.standard.bool(forKey: key(id)) }
    static func markDone(_ id: UUID) { UserDefaults.standard.set(true, forKey: key(id)) }
}
