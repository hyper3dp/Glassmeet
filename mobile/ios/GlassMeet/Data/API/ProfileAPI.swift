import Foundation
import Supabase

enum ProfileAPI {
    /// The signed-in host's profile, created on first load when it's missing (like the web app)
    static func load(email: String?, metadataName: String?) async throws -> Profile {
        let id = try API.uid()
        let rows: [Lossy<Profile>] = try await supabase.from("profiles").select().eq("id", value: id).execute().value
        if let profile = rows.unwrapped().first { return profile }
        let row: Row = [
            "id": .string(id),
            "email": json(email),
            "full_name": .string(metadataName ?? email?.split(separator: "@").first.map(String.init) ?? ""),
        ]
        let created: Profile = try await supabase.from("profiles").insert(row).select().single().execute().value
        return created
    }

    static func update(_ fields: Row) async throws -> Profile {
        let id = try API.uid()
        do {
            let profile: Profile = try await supabase.from("profiles").update(fields).eq("id", value: id).select().single().execute().value
            return profile
        } catch {
            let text = String(describing: error)
            if text.localizedCaseInsensitiveContains("username"), text.contains("duplicate") || text.contains("unique") || text.contains("23505") {
                throw API.Message(text: "That username is taken. Try another one.")
            }
            throw error
        }
    }

    /// Booking pages read the host's name, photo and zone from each event type (guests can't
    /// read profiles), so a profile change is copied onto all of the host's event types.
    static func syncHostFields(name: String?, photo: String?, timezone: String?) async throws {
        var fields: Row = ["host_name": json(name), "host_photo": json(photo)]
        if let timezone, !timezone.isEmpty { fields["host_timezone"] = .string(timezone) }
        try await supabase.from("event_types").update(fields, returning: .minimal).eq("created_by_id", value: API.uid()).execute()
    }

    /// First-run Mon–Fri 9–5 hours, seeded once per host (profiles.hours_seeded)
    static func seedHoursIfNeeded(_ profile: Profile) async {
        guard profile.hoursSeeded == false, let id = try? API.uid() else { return }
        do {
            let claimed: [Lossy<Profile>] = try await supabase.from("profiles").update(["hours_seeded": .bool(true)])
                .eq("id", value: id).eq("hours_seeded", value: "false").select().execute().value
            guard !claimed.isEmpty else { return }
            let rules = try await AvailabilityAPI.rules()
            guard rules.isEmpty else { return }
            let rows: [Row] = (1...5).map { ["day_of_week": .integer($0), "start_time": "09:00", "end_time": "17:00"] }
            try await supabase.from("availability_rules").insert(rows).execute()
        } catch {
            // let the next launch try again
            _ = try? await supabase.from("profiles").update(["hours_seeded": .bool(false)]).eq("id", value: id).execute()
        }
    }

    // MARK: Photo

    /// Uploads to Storage avatars/<uid>/avatar-<timestamp>.<ext> (insert only) and returns the
    /// public URL.
    static func uploadPhoto(_ data: Data, ext: String, contentType: String) async throws -> String {
        let id = try API.uid()
        let path = "\(id)/avatar-\(Int(Date().timeIntervalSince1970 * 1000)).\(ext)"
        let bucket = supabase.storage.from("avatars")
        do {
            try await bucket.upload(path, data: data, options: FileOptions(cacheControl: "31536000", contentType: contentType, upsert: false))
        } catch {
            if String(describing: error).localizedCaseInsensitiveContains("bucket not found") {
                throw API.Message(text: "Photo storage isn’t set up yet.")
            }
            throw error
        }
        return try bucket.getPublicURL(path: path).absoluteString
    }

    // MARK: Account

    /// Deletes the account and everything in it: calendar access is revoked first, then the
    /// login (RPC delete_my_account cascades to every table), then the photos.
    static func deleteAccount() async throws {
        let id = try API.uid()
        _ = try? await Edge.call("google-calendar", ["action": "disconnect"])
        _ = try? await Edge.call("connections", ["action": "disconnect", "provider": "microsoft"])
        do {
            try await supabase.rpc("delete_my_account").execute()
        } catch {
            let text = String(describing: error)
            if text.contains("PGRST202") || text.localizedCaseInsensitiveContains("could not find the function") {
                throw API.Message(text: "Deleting accounts isn’t available yet. Try again later.")
            }
            throw error
        }
        let bucket = supabase.storage.from("avatars")
        if let files = try? await bucket.list(path: id), !files.isEmpty {
            _ = try? await bucket.remove(paths: files.map { "\(id)/\($0.name)" })
        }
        try? await supabase.auth.signOut(scope: .local)
    }

    // MARK: Addresses

    static func addresses() async throws -> [Address] {
        let rows: [Lossy<Address>] = try await supabase.from("addresses").select().order("created_date").execute().value
        return rows.unwrapped()
    }

    static func addAddress(label: String, address: String) async throws -> Address {
        let row: Row = ["label": .string(label), "address": .string(address)]
        let saved: Address = try await supabase.from("addresses").insert(row).select().single().execute().value
        return saved
    }

    static func deleteAddress(_ id: UUID) async throws {
        try await supabase.from("addresses").delete(returning: .minimal).eq("id", value: API.key(id)).execute()
    }

    // MARK: Holidays

    static func holidays(region: String) async throws -> [Holiday] {
        let rows: [Lossy<Holiday>] = try await supabase.from("holidays").select("date, name").eq("region", value: region).order("date").execute().value
        return rows.unwrapped()
    }
}
