import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Photo, name, username, email (read-only), bio, time zone and WhatsApp number. Saving also
/// copies the name, photo and zone onto every event type (booking pages read them there).
struct ProfileView: View {
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var username = ""
    @State private var bio = ""
    @State private var timezone = TimeZone.current.identifier
    @State private var whatsapp = ""
    @State private var photo: String?
    @State private var seeded = false
    @State private var photoItem: PhotosPickerItem?
    @State private var uploading = false
    @State private var saving = false
    @State private var error: String?
    @State private var toast: String?

    private var dirty: Bool {
        guard let p = session.profile, seeded else { return false }
        return name != (p.fullName ?? "") || username != (p.username ?? "") || bio != (p.bio ?? "")
            || timezone != (p.timezone ?? TimeZone.current.identifier) || whatsapp != (p.whatsappNumber ?? "") || photo != p.profilePhoto
    }

    var body: some View {
        Form {
            Section {
                HStack(spacing: Theme.Space.m) {
                    ZStack {
                        Avatar(name: name.isEmpty ? session.hostName : name, photo: photo.flatMap(URL.init(string:)), size: 76)
                        if uploading {
                            Circle().fill(.black.opacity(0.35)).frame(width: 76, height: 76)
                            ProgressView().tint(.white)
                        }
                    }
                    VStack(alignment: .leading, spacing: Theme.Space.xs) {
                        PhotosPicker(selection: $photoItem, matching: .images, photoLibrary: .shared()) {
                            Text(photo == nil ? "Add photo" : "Change photo")
                        }
                        .buttonStyle(SecondaryButtonStyle(compact: true))
                        .disabled(uploading)
                        if photo != nil {
                            Button("Remove photo", role: .destructive) { photo = nil }
                                .font(.subheadline)
                                .buttonStyle(.borderless)
                                .disabled(uploading)
                        }
                    }
                }
                .padding(.vertical, Theme.Space.xxs)
            } footer: {
                Text("JPG, PNG or WebP, up to 5 MB. Guests see it on your booking pages.")
            }
            .listRowBackground(Theme.surface)

            Section("Profile") {
                LabeledField(title: "Name") {
                    TextField("Your name", text: $name)
                        .textContentType(.name)
                        .onChange(of: name) { _, new in if new.count > 80 { name = String(new.prefix(80)) } }
                }
                LabeledField(title: "Username") {
                    TextField("username", text: $username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onChange(of: username) { _, new in
                            let clean = String(new.lowercased().filter { $0.isLetter && $0.isASCII || $0.isNumber || $0 == "_" }.prefix(40))
                            if clean != new { username = clean }
                        }
                }
                LabeledContent("Email", value: session.email ?? "")
                    .foregroundStyle(Theme.textSecondary)
            }
            .listRowBackground(Theme.surface)

            Section {
                TextField("A short intro shown on your booking page", text: $bio, axis: .vertical)
                    .lineLimit(2...6)
                    .onChange(of: bio) { _, new in if new.count > 300 { bio = String(new.prefix(300)) } }
            } header: {
                Text("Welcome message")
            }
            .listRowBackground(Theme.surface)

            Section {
                NavigationLink {
                    TimeZonePicker(selection: $timezone)
                } label: {
                    LabeledContent("Time zone", value: timezone.replacingOccurrences(of: "_", with: " "))
                }
                LabeledField(title: "WhatsApp number") {
                    TextField("+971 50 123 4567", text: $whatsapp)
                        .keyboardType(.phonePad)
                        .textContentType(.telephoneNumber)
                        .onChange(of: whatsapp) { _, new in
                            let clean = String(new.filter { $0.isNumber || "+ ()-".contains($0) }.prefix(24))
                            if clean != new { whatsapp = clean }
                        }
                }
            } footer: {
                Text("Your time zone is used for your hours and every booking. Include the country code in your number.")
            }
            .listRowBackground(Theme.surface)
        }
        .scrollContentBackground(.hidden)
        .background(Theme.background)
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if saving {
                    ProgressView()
                } else {
                    Button("Save") { Task { await save() } }
                        .disabled(!dirty || uploading)
                }
            }
        }
        .onAppear(perform: seed)
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task { await upload(item) }
        }
        .errorAlert($error)
        .toast($toast)
    }

    private func seed() {
        guard !seeded, let p = session.profile else { return }
        name = p.fullName ?? session.hostName
        username = p.username ?? ""
        bio = p.bio ?? ""
        timezone = p.timezone.flatMap { $0.isEmpty ? nil : $0 } ?? TimeZone.current.identifier
        whatsapp = p.whatsappNumber ?? ""
        photo = p.profilePhoto.flatMap { $0.isEmpty ? nil : $0 }
        seeded = true
    }

    private func upload(_ item: PhotosPickerItem) async {
        uploading = true
        defer { uploading = false; photoItem = nil }
        do {
            guard let raw = try await item.loadTransferable(type: Data.self) else { throw API.Message(text: "That photo couldn’t be read.") }
            let type = item.supportedContentTypes.first
            var bytes = raw
            var ext = "jpg"
            var mime = "image/jpeg"
            if type?.conforms(to: .png) == true { ext = "png"; mime = "image/png" }
            else if type?.conforms(to: .webP) == true { ext = "webp"; mime = "image/webp" }
            else if type?.conforms(to: .gif) == true { ext = "gif"; mime = "image/gif" }
            else if type?.conforms(to: .jpeg) != true {
                // HEIC and others: the avatars bucket takes JPG, PNG, WebP or GIF
                guard let image = UIImage(data: raw), let jpeg = image.jpegData(compressionQuality: 0.85) else {
                    throw API.Message(text: "Choose an image file (JPG, PNG or WebP).")
                }
                bytes = jpeg
            }
            if bytes.count > 5 * 1024 * 1024 {
                // too big: a smaller JPEG
                guard let image = UIImage(data: raw), let jpeg = Self.downscaled(image, maxSide: 1600).jpegData(compressionQuality: 0.8),
                      jpeg.count <= 5 * 1024 * 1024 else {
                    throw API.Message(text: "Choose an image under 5 MB.")
                }
                bytes = jpeg; ext = "jpg"; mime = "image/jpeg"
            }
            photo = try await ProfileAPI.uploadPhoto(bytes, ext: ext, contentType: mime)
            await save()
        } catch {
            self.error = "Upload failed. \(error.readable)"
        }
    }

    private static func downscaled(_ image: UIImage, maxSide: CGFloat) -> UIImage {
        let side = max(image.size.width, image.size.height)
        guard side > maxSide else { return image }
        let scale = maxSide / side
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        return UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }

    private func save() async {
        let phone = whatsapp.trimmingCharacters(in: .whitespaces)
        let digits = phone.filter(\.isNumber).count
        if !phone.isEmpty, !(7...15).contains(digits) {
            error = "Enter the WhatsApp number with its country code, such as +971 50 123 4567."
            return
        }
        saving = true
        defer { saving = false }
        let fullName = trimmedOrNil(name)
        let fields: Row = [
            "full_name": json(fullName),
            "username": json(trimmedOrNil(username)),
            "bio": json(trimmedOrNil(bio)),
            "timezone": .string(timezone),
            "whatsapp_number": json(trimmedOrNil(phone)),
            "profile_photo": json(photo),
        ]
        do {
            session.profile = try await ProfileAPI.update(fields)
            try await ProfileAPI.syncHostFields(name: fullName, photo: photo, timezone: timezone)
            await data.loadEventTypes()
            toast = "Saved"
        } catch {
            self.error = error.readable
        }
    }
}

/// A small label over a field, inside a form row
struct LabeledField<Field: View>: View {
    var title: String
    @ViewBuilder var field: Field

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.footnote.weight(.semibold)).foregroundStyle(Theme.textSecondary)
            field
        }
        .padding(.vertical, 2)
    }
}

/// Every time zone, searchable
struct TimeZonePicker: View {
    @Binding var selection: String
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        let all = TimeZone.knownTimeZoneIdentifiers
        let shown = query.isEmpty ? all : all.filter { $0.localizedCaseInsensitiveContains(query.replacingOccurrences(of: " ", with: "_")) }
        List {
            if query.isEmpty, TimeZone.current.identifier != selection {
                Section("This device") {
                    row(TimeZone.current.identifier)
                }
                .listRowBackground(Theme.surface)
            }
            Section {
                ForEach(shown, id: \.self) { row($0) }
            }
            .listRowBackground(Theme.surface)
        }
        .themedList()
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "City or region")
        .navigationTitle("Time zone")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ id: String) -> some View {
        Button {
            selection = id
            dismiss()
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(id.replacingOccurrences(of: "_", with: " ")).foregroundStyle(Theme.text)
                    if let zone = TimeZone(identifier: id) {
                        Text(zone.localizedName(for: .standard, locale: .current) ?? "").font(.footnote).foregroundStyle(Theme.textSecondary)
                    }
                }
                Spacer()
                if id == selection { Image(systemName: "checkmark").foregroundStyle(Theme.primary) }
            }
        }
    }
}
