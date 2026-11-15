import Foundation
import Supabase

enum WhatsAppAPI {
    static func settings() async throws -> WhatsAppSettings? {
        let rows: [Lossy<WhatsAppSettings>] = try await supabase.from("whatsapp_settings").select()
            .eq("created_by_id", value: API.uid()).execute().value
        return rows.unwrapped().first
    }

    /// One row per host (created_by_id is unique)
    static func save(phone: String?, confirmation: String?, reminder24h: String?, reminder1h: String?) async throws -> WhatsAppSettings {
        let id = try API.uid()
        let row: Row = [
            "created_by_id": .string(id),
            "phone_number": json(phone),
            "confirmation_template": json(confirmation),
            "reminder_24h_template": json(reminder24h),
            "reminder_1h_template": json(reminder1h),
        ]
        return try await supabase.from("whatsapp_settings").upsert(row, onConflict: "created_by_id").select().single().execute().value
    }
}
