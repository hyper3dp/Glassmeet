import SwiftUI

struct AvailabilityView: View {
    @Environment(Session.self) private var session
    @State private var model = AvailabilityModel()
    @State private var addingOverride = false

    /// Monday first (day_of_week: 0 = Sunday)
    private let order = [1, 2, 3, 4, 5, 6, 0]

    var body: some View {
        List {
            if let saveError = model.saveError {
                Section {
                    Label(saveError, systemImage: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                        .foregroundStyle(Theme.danger)
                }
                .listRowBackground(Theme.danger.opacity(0.1))
            }

            Section {
                ForEach(order, id: \.self) { day in
                    DayHoursRow(day: day, model: model)
                }
            } header: {
                Text("Weekly hours")
            } footer: {
                Text("Guests can book inside these hours, in \(session.timeZone.identifier). Changes save automatically.")
            }
            .listRowBackground(Theme.surface)

            overrides

            DailyLimitSection()

            HolidaysSection()
        }
        .themedList()
        .overlay {
            if !model.state.isLoaded, model.ranges.isEmpty {
                if let error = model.state.error {
                    ErrorStateView(message: error) { await model.load() }
                } else {
                    LoadingView()
                }
            }
        }
        .navigationTitle("Availability")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if model.saving > 0 {
                    ProgressView().accessibilityLabel("Saving")
                } else if model.state.isLoaded {
                    Image(systemName: "checkmark.icloud")
                        .foregroundStyle(Theme.textSecondary)
                        .accessibilityLabel("Saved")
                }
            }
        }
        .sheet(isPresented: $addingOverride) {
            DateOverrideSheet { date, type, start, end, label in
                model.addException(date: date, type: type, start: start, end: end, label: label)
            }
        }
        .refreshable { await model.load() }
        .task { if !model.state.isLoaded { await model.load() } }
    }

    private var overrides: some View {
        let today = Fmt.localKey(.now)
        let upcoming = model.exceptions.filter { $0.date >= today }
        return Section {
            if upcoming.isEmpty {
                Text("No date-specific hours yet. Add days off or one-off hours.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.textSecondary)
            }
            ForEach(upcoming) { e in
                VStack(alignment: .leading, spacing: 2) {
                    Text(Fmt.keyLabel(e.date)).font(.headline).foregroundStyle(Theme.text)
                    Text([e.summary, e.label].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.subheadline)
                        .foregroundStyle(e.type == "blocked" ? Theme.warning : Theme.textSecondary)
                }
                .swipeActions {
                    Button("Remove", systemImage: "trash", role: .destructive) { model.removeException(e.id) }
                }
                .contextMenu {
                    Button("Remove", systemImage: "trash", role: .destructive) { model.removeException(e.id) }
                }
            }
            Button { addingOverride = true } label: {
                Label("Add a date", systemImage: "plus.circle.fill")
            }
            .foregroundStyle(Theme.link)
        } header: {
            Text("Date overrides")
        } footer: {
            Text("Swipe left to remove one.")
        }
        .listRowBackground(Theme.surface)
    }
}

/// A weekday's switch and its time ranges
private struct DayHoursRow: View {
    var day: Int
    var model: AvailabilityModel

    var body: some View {
        let ranges = model.ranges(on: day)
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            Toggle(isOn: Binding(get: { !ranges.isEmpty }, set: { model.setDay(day, on: $0) })) {
                Text(Calendar.current.weekdaySymbols[day]).font(.headline).foregroundStyle(Theme.text)
            }
            .tint(Theme.primary)
            if ranges.isEmpty {
                Text("Unavailable").font(.subheadline).foregroundStyle(Theme.textTertiary)
            }
            ForEach(ranges) { range in
                HStack(spacing: Theme.Space.xs) {
                    DatePicker("From", selection: Binding(get: { Fmt.pickerDate(range.start) }, set: { model.setStart(range.id, Fmt.minutes(of: $0)) }),
                               displayedComponents: .hourAndMinute)
                        .labelsHidden()
                    Text("–").foregroundStyle(Theme.textSecondary)
                    DatePicker("To", selection: Binding(get: { Fmt.pickerDate(range.end) }, set: { model.setEnd(range.id, Fmt.minutes(of: $0)) }),
                               displayedComponents: .hourAndMinute)
                        .labelsHidden()
                    Spacer(minLength: 0)
                    Button { model.remove(range.id) } label: {
                        Image(systemName: "minus.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Theme.danger)
                            .frame(width: 36, height: Theme.minTouch)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Remove \(Fmt.clockLabel(range.start)) to \(Fmt.clockLabel(range.end))")
                }
                .disabled(range.serverID == nil)
            }
            if !ranges.isEmpty, model.nextRange(on: day) != nil {
                Button { model.addRange(on: day) } label: {
                    Label("Add hours", systemImage: "plus")
                        .font(.subheadline.weight(.semibold))
                }
                .buttonStyle(.borderless)
                .foregroundStyle(Theme.link)
            }
        }
        .padding(.vertical, Theme.Space.xxs)
    }
}

/// Add a date off, blocked hours or custom hours
private struct DateOverrideSheet: View {
    var add: (Date, String, Int?, Int?, String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var date = Calendar.current.startOfDay(for: .now)
    @State private var mode = Mode.dayOff
    @State private var start = 9 * 60
    @State private var end = 17 * 60
    @State private var label = ""
    @State private var problem: String?

    private enum Mode: String, CaseIterable, Identifiable {
        case dayOff = "Day off", block = "Block hours", custom = "Custom hours"
        var id: String { rawValue }
        var help: String {
            switch self {
            case .dayOff: "No bookings on this date."
            case .block: "Close these hours; the rest of the day stays open."
            case .custom: "These hours replace your usual hours on this date."
            }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    DatePicker("Date", selection: $date, in: Calendar.current.startOfDay(for: .now)..., displayedComponents: .date)
                }
                .listRowBackground(Theme.surface)
                Section {
                    Picker("On this date", selection: $mode) {
                        ForEach(Mode.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    if mode != .dayOff {
                        DatePicker("From", selection: Binding(get: { Fmt.pickerDate(start) }, set: { start = Fmt.minutes(of: $0) }), displayedComponents: .hourAndMinute)
                        DatePicker("To", selection: Binding(get: { Fmt.pickerDate(end) }, set: { let m = Fmt.minutes(of: $0); end = m == 0 ? 1440 : m }), displayedComponents: .hourAndMinute)
                    }
                } footer: {
                    Text(mode.help)
                }
                .listRowBackground(Theme.surface)
                Section {
                    TextField("Label, such as Holiday (optional)", text: $label)
                } footer: {
                    if let problem { Text(problem).foregroundStyle(Theme.danger) }
                }
                .listRowBackground(Theme.surface)
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Change a date")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add", systemImage: "checkmark") { save() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func save() {
        if mode != .dayOff, end <= start { problem = "The end time must be after the start time."; return }
        let text = trimmedOrNil(String(label.prefix(60)))
        switch mode {
        case .dayOff: add(date, "blocked", nil, nil, text)
        case .block: add(date, "blocked", start, end, text)
        case .custom: add(date, "available", start, end, text)
        }
        dismiss()
    }
}

/// The most meetings a day across all event types (profiles.max_per_day)
private struct DailyLimitSection: View {
    @Environment(Session.self) private var session
    @State private var on = false
    @State private var limit = 5
    @State private var seeded = false
    @State private var saveTask: Task<Void, Never>?
    @State private var error: String?

    var body: some View {
        Section {
            Toggle("Daily meeting limit", isOn: $on).tint(Theme.primary)
            if on {
                Stepper(value: $limit, in: 1...100) {
                    LabeledContent("Meetings per day", value: "\(limit)")
                }
            }
        } header: {
            Text("Daily limit")
        } footer: {
            Text(error ?? "Once you have this many meetings on a day, guests can’t book more that day. It covers all your event types.")
                .foregroundStyle(error == nil ? Theme.textSecondary : Theme.danger)
        }
        .listRowBackground(Theme.surface)
        .onAppear(perform: seed)
        .onChange(of: session.profile?.maxPerDay) { _, _ in seeded = false; seed() }
        .onChange(of: on) { _, _ in schedule() }
        .onChange(of: limit) { _, _ in schedule() }
    }

    private func seed() {
        guard !seeded, let profile = session.profile else { return }
        on = profile.maxPerDay != nil
        limit = profile.maxPerDay ?? 5
        seeded = true
    }

    private func schedule() {
        guard seeded else { return }
        let value: Int? = on ? limit : nil
        guard value != session.profile?.maxPerDay else { return }
        saveTask?.cancel()
        saveTask = Task {
            try? await Task.sleep(for: .milliseconds(700))
            guard !Task.isCancelled else { return }
            do {
                session.profile = try await ProfileAPI.update(["max_per_day": json(value)])
                error = nil
            } catch {
                self.error = "Couldn’t save the daily limit. \(error.readable)"
            }
        }
    }
}
