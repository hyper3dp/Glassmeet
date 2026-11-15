import SwiftUI

/// Create or edit an event type: Basics, When, Rules, Booking form, Options and Delete.
/// Save and Save as template float at the bottom on Liquid Glass.
struct EventTypeFormView: View {
    var source: EventFormSource
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(\.dismiss) private var dismiss

    @State private var draft = EventTypeDraft()
    @State private var initial = EventTypeDraft()
    @State private var loaded = false
    @State private var loadError: String?
    @State private var saving = false
    @State private var savingTemplate = false
    @State private var problem: String?
    @State private var error: String?
    @State private var toast: String?
    @State private var confirmDiscard = false
    @State private var confirmDelete = false
    @State private var addresses: [Address] = []
    @State private var addressChoice: UUID?
    @State private var newAddressLabel = ""
    @State private var saveNewAddress = true

    private var editingID: UUID? {
        if case .edit(let id) = source { return id }
        return nil
    }
    private var dirty: Bool { loaded && draft != initial }

    var body: some View {
        Group {
            if loaded {
                form
            } else if let loadError {
                ErrorStateView(message: loadError) { await load() }
            } else {
                LoadingView()
            }
        }
        .background(Theme.background)
        .navigationTitle(editingID == nil ? "New event type" : "Edit event type")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(dirty)
        .toolbar {
            if dirty {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { confirmDiscard = true }
                }
            }
        }
        .confirmationDialog("Discard your changes?", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("Discard changes", role: .destructive) { dismiss() }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text("You have changes to this event that aren’t saved yet.")
        }
        .confirmationDialog("Delete this event type?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { Task { await delete() } }
            if initial.active {
                Button("Turn off instead") { Task { await turnOff() } }
            }
            Button("Keep it", role: .cancel) {}
        } message: {
            Text("Its booking link stops working. Meetings already booked stay on your calendar.")
        }
        .task { if !loaded { await load() } }
        .errorAlert($error)
        .toast($toast)
    }

    // MARK: Form

    private var form: some View {
        Form {
            basics
            location
            when
            rules
            bookingForm
            options
            if editingID != nil {
                Section {
                    Button("Delete event type", role: .destructive) { confirmDelete = true }
                        .frame(maxWidth: .infinity)
                }
                .listRowBackground(Theme.surface)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.background)
        .scrollDismissesKeyboard(.interactively)
        .safeAreaBar(edge: .bottom) { actionBar }
        .animation(.snappy, value: draft.kind)
        .animation(.snappy, value: draft.useWorkingHours)
        .animation(.snappy, value: draft.repeats)
        .animation(.snappy, value: draft.useFixedTimes)
    }

    private var actionBar: some View {
        VStack(spacing: Theme.Space.xs) {
            if let problem {
                Label(problem, systemImage: "exclamationmark.circle.fill")
                    .font(.subheadline)
                    .foregroundStyle(Theme.danger)
                    .padding(.horizontal, Theme.Space.m)
                    .padding(.vertical, Theme.Space.xs)
                    .background(Theme.surface, in: Capsule())
            }
            GlassEffectContainer(spacing: Theme.Space.s) {
                HStack(spacing: Theme.Space.s) {
                    Button { Task { await saveTemplate() } } label: {
                        Text(savingTemplate ? "Saving…" : "Save as template").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                    .disabled(savingTemplate)
                    Button { Task { await save() } } label: {
                        Text(saving ? "Saving…" : editingID == nil ? "Create" : "Save").fontWeight(.semibold).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .disabled(saving)
                }
                .controlSize(.large)
            }
        }
        .padding(.horizontal, Theme.Space.m)
        .padding(.bottom, Theme.Space.xs)
    }

    // MARK: 1. Basics

    private var basics: some View {
        Section {
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                ForEach(EventTypeDraft.Kind.allCases) { kind in
                    KindOption(kind: kind, selected: draft.kind == kind) { draft.kind = kind }
                }
            }
            .padding(.vertical, Theme.Space.xxs)
            if draft.kind == .group {
                Stepper(value: $draft.maxAttendees, in: 2...500) {
                    LabeledContent("Spots per time", value: "\(draft.maxAttendees)")
                }
            }
            TextField("Name, such as 30 minute meeting", text: $draft.name)
                .onChange(of: draft.name) { _, new in if new.count > 80 { draft.name = String(new.prefix(80)) } }
            VStack(alignment: .leading, spacing: Theme.Space.xs) {
                Text("Duration").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text)
                FlowLayout {
                    ForEach(EventTypeDraft.durations, id: \.self) { d in
                        ChoiceChip(title: Fmt.duration(d), selected: !draft.customDuration && draft.duration == d) {
                            draft.customDuration = false
                            draft.duration = d
                        }
                    }
                    ChoiceChip(title: "Custom", selected: draft.customDuration) { draft.customDuration = true }
                }
            }
            .padding(.vertical, Theme.Space.xxs)
            if draft.customDuration {
                Stepper(value: $draft.duration, in: 5...720, step: 5) {
                    LabeledContent("Length", value: Fmt.duration(draft.duration))
                }
            }
        } header: {
            Text("Basics")
        }
        .listRowBackground(Theme.surface)
    }

    private var location: some View {
        Section {
            Picker("Location", selection: $draft.location) {
                ForEach(EventTypeDraft.locations, id: \.name) { Text($0.name).tag($0.name) }
            }
            switch draft.locationKind {
            case .other:
                TextField("Where, such as Webex or Signal", text: $draft.customLocation)
                linkField
            case .video:
                linkField
            case .phone:
                Text("You call the guest on the WhatsApp number they give when booking.")
                    .font(.subheadline).foregroundStyle(Theme.textSecondary)
            case .inPerson:
                if !addresses.isEmpty {
                    Picker("Address", selection: $addressChoice) {
                        ForEach(addresses) { Text($0.title).tag(Optional($0.id)) }
                        Text("New address").tag(UUID?.none)
                    }
                    .onChange(of: addressChoice) { _, id in
                        draft.address = addresses.first { $0.id == id }?.address ?? ""
                    }
                }
                if addressChoice == nil {
                    TextField("Street, building, city", text: $draft.address, axis: .vertical)
                        .lineLimit(2...4)
                    TextField("Label, such as Office (optional)", text: $newAddressLabel)
                    Toggle("Save to my addresses", isOn: $saveNewAddress).tint(Theme.primary)
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                TextField("Description guests see (optional)", text: $draft.description, axis: .vertical)
                    .lineLimit(2...6)
                    .onChange(of: draft.description) { _, new in if new.count > 500 { draft.description = String(new.prefix(500)) } }
                Text("\(draft.description.count)/500")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(draft.description.count >= 500 ? Theme.danger : Theme.textTertiary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
            TextField("Private note, only you see it (optional)", text: $draft.note, axis: .vertical)
                .lineLimit(1...5)
                .onChange(of: draft.note) { _, new in if new.count > 2000 { draft.note = String(new.prefix(2000)) } }
        } header: {
            Text("Location and details")
        } footer: {
            if draft.locationKind == .video {
                Text(draft.location == "Zoom"
                     ? "Paste your Zoom link. Guests get it after they book."
                     : "Leave the link blank to create a new one for each booking. \(draft.location == "Google Meet" ? "Google" : "Microsoft") must be connected in Calendar connections.")
            }
        }
        .listRowBackground(Theme.surface)
    }

    private var linkField: some View {
        TextField("Meeting link (optional)", text: $draft.meetingURL)
            .keyboardType(.URL)
            .textContentType(.URL)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
    }

    // MARK: 2. When

    @ViewBuilder private var when: some View {
        Section {
            if draft.kind == .oneOff {
                datesPicker
                fixedTimesEditor(title: "Start time")
            } else {
                Picker("Availability", selection: $draft.useWorkingHours) {
                    Text("Working hours").tag(true)
                    Text("Custom").tag(false)
                }
                .pickerStyle(.segmented)
                .listRowSeparator(.hidden)
                if draft.useWorkingHours {
                    Text("Guests pick any free time inside your weekly hours from Availability.")
                        .font(.subheadline).foregroundStyle(Theme.textSecondary)
                } else {
                    Picker("Dates", selection: $draft.repeats) {
                        Text("Repeats").tag(true)
                        Text("Specific dates").tag(false)
                    }
                    .pickerStyle(.segmented)
                    if draft.repeats {
                        Stepper(value: $draft.every, in: 1...52) {
                            LabeledContent("Every", value: "\(draft.every) \(draft.freq.label(draft.every))")
                        }
                        Picker("Repeats every", selection: $draft.freq) {
                            ForEach(EventTypeDraft.Freq.allCases) { Text($0.label(draft.every).capitalized).tag($0) }
                        }
                        if draft.freq == .week { weekdays }
                    } else {
                        datesPicker
                    }
                }
            }
        } header: {
            Text("When")
        } footer: {
            Text(draft.scheduleSummary)
        }
        .listRowBackground(Theme.surface)

        if draft.kind != .oneOff, !draft.useWorkingHours {
            Section {
                Picker("Times", selection: $draft.useFixedTimes) {
                    Text("From my hours").tag(false)
                    Text("Fixed times").tag(true)
                }
                .pickerStyle(.segmented)
                if draft.useFixedTimes { fixedTimesEditor(title: "Start times") }
            } header: {
                Text("Times")
            } footer: {
                Text(draft.useFixedTimes
                     ? "Guests can only book these start times, in your time zone. Days or hours you block in Availability still apply."
                     : "Guests pick any free time inside your hours in Availability.")
            }
            .listRowBackground(Theme.surface)
        }

        if draft.kind != .oneOff, draft.useWorkingHours || draft.repeats {
            Section {
                DatePicker("Starts", selection: $draft.startDate, displayedComponents: .date)
                Toggle("Ends on a date", isOn: $draft.hasEndDate).tint(Theme.primary)
                if draft.hasEndDate {
                    DatePicker("Ends", selection: $draft.endDate, in: draft.startDate..., displayedComponents: .date)
                } else {
                    Stepper(value: $draft.maxFutureDays, in: 1...730) {
                        LabeledContent("How far ahead", value: Fmt.plural(draft.maxFutureDays, "day"))
                    }
                }
            } header: {
                Text("Date range")
            } footer: {
                Text(draft.hasEndDate ? "Guests can book between these dates." : "Guests can book up to \(Fmt.plural(draft.maxFutureDays, "calendar day")) ahead.")
            }
            .listRowBackground(Theme.surface)
        }
    }

    private var weekdays: some View {
        let symbols = Calendar.current.veryShortWeekdaySymbols
        return HStack(spacing: 6) {
            ForEach([1, 2, 3, 4, 5, 6, 0], id: \.self) { day in
                let on = draft.days.contains(day)
                Button {
                    if on { draft.days.remove(day) } else { draft.days.insert(day) }
                } label: {
                    Text(symbols[day])
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(on ? Theme.onPrimary : Theme.text)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background(on ? Theme.primary : Theme.surfaceRaised, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Calendar.current.weekdaySymbols[day])
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(.vertical, Theme.Space.xxs)
    }

    private var datesPicker: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            MultiDatePicker("Dates", selection: $draft.dates, in: Calendar.current.startOfDay(for: .now)...)
                .tint(Theme.primary)
            Text(draft.dates.isEmpty ? "Tap the days guests can book." : "\(Fmt.plural(draft.dates.count, "date")) picked")
                .font(.footnote).foregroundStyle(Theme.textSecondary)
        }
    }

    private func fixedTimesEditor(title: String) -> some View {
        Group {
            ForEach(Array(draft.fixedTimes.enumerated()), id: \.offset) { index, _ in
                HStack {
                    DatePicker(draft.fixedTimes.count > 1 ? "\(title) \(index + 1)" : title, selection: timeBinding(index), displayedComponents: .hourAndMinute)
                    if draft.fixedTimes.count > 1 {
                        Button {
                            if draft.fixedTimes.indices.contains(index) { draft.fixedTimes.remove(at: index) }
                        } label: {
                            Image(systemName: "minus.circle.fill").foregroundStyle(Theme.danger)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Remove start time \(index + 1)")
                    }
                }
            }
            if draft.fixedTimes.count < 48 {
                Button {
                    let next = min(23 * 60, (draft.fixedTimes.max() ?? 9 * 60 - 60) + 60)
                    draft.fixedTimes.append(next)
                } label: {
                    Label("Add a start time", systemImage: "plus.circle.fill")
                }
                .foregroundStyle(Theme.link)
            }
        }
    }

    private func timeBinding(_ index: Int) -> Binding<Date> {
        Binding(
            get: { Fmt.pickerDate(draft.fixedTimes.indices.contains(index) ? draft.fixedTimes[index] : 540) },
            set: { date in if draft.fixedTimes.indices.contains(index) { draft.fixedTimes[index] = Fmt.minutes(of: date) } }
        )
    }

    // MARK: 3. Rules

    private var rules: some View {
        Section {
            HStack {
                Stepper(value: $draft.noticeValue, in: 0...999) {
                    LabeledContent("Minimum notice", value: "\(draft.noticeValue)")
                }
            }
            Picker("Notice in", selection: $draft.noticeUnit) {
                ForEach(EventTypeDraft.NoticeUnit.allCases) { Text($0.rawValue.capitalized).tag($0) }
            }
            .pickerStyle(.segmented)
            Toggle("Limit meetings per day", isOn: $draft.limitPerDay).tint(Theme.primary)
            if draft.limitPerDay {
                Stepper(value: $draft.maxPerDay, in: 1...100) {
                    LabeledContent("Per day", value: "\(draft.maxPerDay)")
                }
            }
            Stepper(value: $draft.bufferBefore, in: 0...240, step: 5) {
                LabeledContent("Buffer before", value: "\(draft.bufferBefore) min")
            }
            Stepper(value: $draft.bufferAfter, in: 0...240, step: 5) {
                LabeledContent("Buffer after", value: "\(draft.bufferAfter) min")
            }
        } header: {
            Text("Rules")
        } footer: {
            Text("Guests can’t book closer than the minimum notice. Buffers keep time clear around each meeting.")
        }
        .listRowBackground(Theme.surface)
    }

    // MARK: 4. Booking form

    private var bookingForm: some View {
        Section {
            ForEach($draft.questions) { $q in
                QuestionEditor(question: $q) {
                    draft.questions.removeAll { $0.id == q.id }
                }
            }
            if draft.questions.count < 10 {
                Button {
                    draft.questions.append(.init())
                } label: {
                    Label("Add a question", systemImage: "plus.circle.fill")
                }
                .foregroundStyle(Theme.link)
            }
            Toggle("Ask for email", isOn: $draft.emailRequired).tint(Theme.primary)
        } header: {
            Text("Booking form")
        } footer: {
            Text("Guests always give their name and WhatsApp number. Up to 10 questions. With Ask for email off, email is optional.")
        }
        .listRowBackground(Theme.surface)
    }

    // MARK: 5. Options

    private var options: some View {
        Section {
            Toggle(isOn: $draft.requiresApproval) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Requires approval")
                    Text("You approve each booking before it’s confirmed.").font(.footnote).foregroundStyle(Theme.textSecondary)
                }
            }
            .tint(Theme.primary)
            Toggle(isOn: $draft.active) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Bookable")
                    Text("Guests can book this event from its link.").font(.footnote).foregroundStyle(Theme.textSecondary)
                }
            }
            .tint(Theme.primary)
        } header: {
            Text("Options")
        }
        .listRowBackground(Theme.surface)
    }

    // MARK: Data

    private func load() async {
        loadError = nil
        async let addressList = try? ProfileAPI.addresses()
        switch source {
        case .edit(let id):
            do {
                let event = try await EventTypesAPI.get(id)
                draft = EventTypeDraft(event, isNew: false)
            } catch {
                loadError = error.readable
                return
            }
        case .new(let prefill):
            draft = prefill.map { EventTypeDraft($0, isNew: true) } ?? EventTypeDraft()
        }
        addresses = await addressList ?? []
        if let match = addresses.first(where: { $0.address == draft.address && !draft.address.isEmpty }) {
            addressChoice = match.id
        } else if draft.address.isEmpty, let first = addresses.first {
            addressChoice = first.id
            draft.address = first.address ?? ""
        }
        initial = draft
        loaded = true
    }

    private func save() async {
        problem = draft.problem()
        guard problem == nil else { return }
        saving = true
        defer { saving = false }
        do {
            if draft.locationKind == .inPerson, addressChoice == nil, saveNewAddress {
                if let saved = try? await ProfileAPI.addAddress(label: newAddressLabel.trimmingCharacters(in: .whitespaces),
                                                                 address: draft.address.trimmingCharacters(in: .whitespacesAndNewlines)) {
                    addresses.append(saved)
                    addressChoice = saved.id
                }
            }
            let saved = try await EventTypesAPI.save(editingID, fields: draft.fields(), profile: session.profile)
            data.update(saved)
            initial = draft
            dismiss()
        } catch {
            self.error = "This event type couldn’t be saved. \(error.readable)"
        }
    }

    private func saveTemplate() async {
        let name = draft.name.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { problem = "Give the event a name first; the template uses it."; return }
        problem = nil
        savingTemplate = true
        defer { savingTemplate = false }
        do {
            _ = try await EventTypesAPI.saveTemplate(name: name, data: EventTypesAPI.templateData(draft.fields()))
            toast = "Saved “\(name)” as a template"
        } catch {
            self.error = "Couldn’t save the template. \(error.readable)"
        }
    }

    private func delete() async {
        guard let id = editingID else { return }
        do {
            try await EventTypesAPI.delete(id)
            data.remove(eventType: id)
            initial = draft
            dismiss()
        } catch {
            self.error = "This event type couldn’t be deleted. \(error.readable)"
        }
    }

    private func turnOff() async {
        guard let id = editingID else { return }
        do {
            try await EventTypesAPI.setActive(id, false)
            draft.active = false
            initial.active = false
            await data.loadEventTypes()
            toast = "Turned off"
        } catch {
            self.error = error.readable
        }
    }
}

/// One of One-on-one / Group / One-off
private struct KindOption: View {
    var kind: EventTypeDraft.Kind
    var selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Theme.Space.s) {
                Image(systemName: kind.icon)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(selected ? Theme.link : Theme.textSecondary)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 1) {
                    Text(kind.label).font(.subheadline.weight(.semibold)).foregroundStyle(selected ? Theme.link : Theme.text)
                    Text(kind.hint).font(.footnote).foregroundStyle(Theme.textSecondary)
                }
                Spacer(minLength: 0)
                if selected {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.primary)
                }
            }
            .padding(.horizontal, Theme.Space.s)
            .frame(minHeight: 52)
            .background(selected ? Theme.primarySoft : Theme.surfaceRaised, in: .rect(cornerRadius: Theme.Radius.field))
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// One invitee question: text, answer type, required, options
private struct QuestionEditor: View {
    @Binding var question: EventTypeDraft.Question
    var remove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xs) {
            HStack {
                TextField("Your question", text: $question.text)
                    .font(.body.weight(.semibold))
                Button(action: remove) {
                    Image(systemName: "trash").foregroundStyle(Theme.danger)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Remove question")
            }
            Picker("Answer", selection: $question.kind) {
                ForEach(CustomQuestion.Kind.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.menu)
            .tint(Theme.link)
            Toggle("Required", isOn: $question.required).tint(Theme.primary)
            if question.kind.hasOptions {
                TextField("One answer per line", text: $question.options, axis: .vertical)
                    .lineLimit(2...8)
                    .padding(Theme.Space.xs)
                    .background(Theme.surfaceRaised, in: .rect(cornerRadius: Theme.Radius.field))
            }
        }
        .padding(.vertical, Theme.Space.xxs)
    }
}
