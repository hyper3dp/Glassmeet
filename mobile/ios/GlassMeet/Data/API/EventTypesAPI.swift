import Foundation
import Supabase

enum EventTypesAPI {
    /// The host's event types, newest first
    static func list() async throws -> [EventType] {
        let rows: [Lossy<EventType>] = try await supabase.from("event_types").select()
            .eq("created_by_id", value: API.uid())
            .order("created_date", ascending: false)
            .execute().value
        return rows.unwrapped()
    }

    static func get(_ id: UUID) async throws -> EventType {
        try await supabase.from("event_types").select().eq("id", value: API.key(id)).single().execute().value
    }

    /// Saves the form's fields with the host's name, photo and zone (booking pages read them
    /// from the event type; without host_timezone, slots would shift to UTC).
    static func save(_ id: UUID?, fields: Row, profile: Profile?) async throws -> EventType {
        var row = fields
        row["host_name"] = json(trimmedOrNil(profile?.fullName ?? ""))
        row["host_photo"] = json(trimmedOrNil(profile?.profilePhoto ?? ""))
        row["host_timezone"] = .string(trimmedOrNil(profile?.timezone ?? "") ?? TimeZone.current.identifier)
        if let id {
            return try await supabase.from("event_types").update(row).eq("id", value: API.key(id)).select().single().execute().value
        }
        return try await supabase.from("event_types").insert(row).select().single().execute().value
    }

    static func setActive(_ id: UUID, _ active: Bool) async throws {
        try await supabase.from("event_types").update(["active": .bool(active)] as Row, returning: .minimal)
            .eq("id", value: API.key(id)).execute()
    }

    static func delete(_ id: UUID) async throws {
        try await supabase.from("event_types").delete(returning: .minimal).eq("id", value: API.key(id)).execute()
    }

    // MARK: Templates (event_templates); the table may be missing on older databases

    static func templates() async -> [EventTemplate] {
        guard let id = try? API.uid() else { return [] }
        let rows: [Lossy<EventTemplate>]? = try? await supabase.from("event_templates").select()
            .eq("created_by_id", value: id).order("created_date", ascending: false).execute().value
        return rows?.unwrapped() ?? []
    }

    /// event_templates.name must be 1-80 characters
    static func templateName(_ raw: String) -> String {
        let name = String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80)).trimmingCharacters(in: .whitespaces)
        return name.isEmpty ? "Untitled template" : name
    }

    static func saveTemplate(name raw: String, data: Row) async throws -> EventTemplate {
        let name = templateName(raw)
        var fields = data
        fields["name"] = .string(name)
        let row: Row = ["name": .string(name), "data": .object(fields)]
        return try await supabase.from("event_templates").insert(row).select().single().execute().value
    }

    static func deleteTemplate(_ id: UUID) async throws {
        try await supabase.from("event_templates").delete(returning: .minimal).eq("id", value: API.key(id)).execute()
    }

    /// The fields a template keeps, from an event type's saved row
    static func templateData(_ fields: Row) -> Row {
        fields.filter { TemplateChoice.fields.contains($0.key) }
    }

    /// An existing event's fields as a template-shaped row (for Duplicate and Save as template)
    static func fields(of event: EventType) -> Row {
        var row = Row()
        row["name"] = json(event.name)
        row["description"] = json(event.description)
        row["note"] = json(event.note)
        row["duration"] = json(event.duration)
        row["location"] = json(event.location)
        row["meeting_url"] = json(event.meetingURL)
        row["address"] = json(event.address)
        row["booking_type"] = json(event.bookingType)
        row["max_attendees"] = json(event.maxAttendees)
        row["requires_approval"] = .bool(event.needsApproval)
        row["email_required"] = .bool(event.emailRequired ?? true)
        row["schedule"] = json(event.schedule)
        row["freq"] = json(event.freq)
        row["every"] = json(event.every)
        row["days"] = json(event.days)
        row["fixed_times"] = json(event.fixedTimes)
        row["max_future_days"] = json(event.maxFutureDays)
        row["min_notice"] = json(event.minNotice)
        row["max_per_day"] = json(event.maxPerDay)
        row["buffer_before"] = json(event.bufferBefore)
        row["buffer_after"] = json(event.bufferAfter)
        row["custom_questions"] = .array(event.questions.map(\.jsonValue))
        return row.filter { !$0.value.isNil }
    }
}
