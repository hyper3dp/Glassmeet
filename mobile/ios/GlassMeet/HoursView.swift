import SwiftUI

struct HoursView: View {
    /// Monday first, Sunday last (day_of_week: 0 = Sunday)
    private let order = [1, 2, 3, 4, 5, 6, 0]
    @State private var rules: [AvailabilityRule] = []
    @State private var loaded = false
    @State private var saving: Int?
    @State private var error: String?

    var body: some View {
        List {
            Section {
                ForEach(order, id: \.self) { day in
                    DayRow(day: day, rules: rules.filter { $0.dayOfWeek == day }, saving: saving == day) { from, to in
                        await setDay(day, from: from, to: to)
                    }
                }
            } footer: {
                Text("Guests can book inside these hours. Dates off, holidays and split days are set on the web.")
            }
        }
        .navigationTitle("Working hours")
        .overlay { if !loaded { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .errorAlert($error)
    }

    private func load() async {
        do { rules = try await API.availability() } catch { self.error = error.readable }
        loaded = true
    }

    private func setDay(_ day: Int, from: String?, to: String?) async {
        if let from, let to, from >= to {
            error = "The end time must be after the start time."
            return
        }
        saving = day
        defer { saving = nil }
        do { try await API.setDay(day, from: from, to: to) } catch {
            self.error = "\(Calendar.current.weekdaySymbols[day])’s hours could not be saved. Try again."
        }
        await load()
    }
}

private struct DayRow: View {
    var day: Int
    var rules: [AvailabilityRule]
    var saving: Bool
    var save: (String?, String?) async -> Void

    var body: some View {
        let name = Calendar.current.weekdaySymbols[day]
        VStack(alignment: .leading, spacing: 10) {
            Toggle(isOn: Binding(get: { !rules.isEmpty }, set: { on in
                Task { await on ? save("09:00", "17:00") : save(nil, nil) }
            })) {
                Text(name).fontWeight(.semibold)
            }
            .disabled(rules.count > 1 || saving)

            if rules.count > 1 {
                // Split days are set on the web; shown here, not edited
                Text(rules.map { "\(Format.time(Format.clock($0.startTime))) to \(Format.time(Format.clock($0.endTime)))" }.joined(separator: ", "))
                    .font(.subheadline).foregroundStyle(.secondary)
            } else if let rule = rules.first {
                HStack {
                    DatePicker("From", selection: Binding(get: { Format.clock(rule.startTime) }, set: { date in
                        Task { await save(Format.clockString(date), String(rule.endTime.prefix(5))) }
                    }), displayedComponents: .hourAndMinute)
                    DatePicker("To", selection: Binding(get: { Format.clock(rule.endTime) }, set: { date in
                        Task { await save(String(rule.startTime.prefix(5)), Format.clockString(date)) }
                    }), displayedComponents: .hourAndMinute)
                }
                .font(.subheadline)
                .disabled(saving)
            }
        }
        .opacity(saving ? 0.5 : 1)
        .padding(.vertical, 4)
    }
}
