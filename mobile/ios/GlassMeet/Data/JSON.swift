import Foundation
import Supabase

/// A row to insert or update. Values are AnyJSON so a nil clears the column (null) instead of
/// being left out.
typealias Row = [String: AnyJSON]

func json(_ value: String?) -> AnyJSON { value.map { AnyJSON.string($0) } ?? .null }
func json(_ value: Int?) -> AnyJSON { value.map { AnyJSON.integer($0) } ?? .null }
func json(_ value: Bool) -> AnyJSON { .bool(value) }
func json(_ value: [String]?) -> AnyJSON { value.map { AnyJSON.array($0.map { AnyJSON.string($0) }) } ?? .null }
func json(_ value: [Int]?) -> AnyJSON { value.map { AnyJSON.array($0.map { AnyJSON.integer($0) }) } ?? .null }

/// Empty or whitespace-only text is stored as null
func trimmedOrNil(_ text: String) -> String? {
    let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
    return t.isEmpty ? nil : t
}

extension AnyJSON {
    /// Text for any scalar: strings as they are, numbers and booleans written out
    var text: String? {
        switch self {
        case .string(let s): return s
        case .integer(let i): return String(i)
        case .double(let d): return d.rounded() == d ? String(Int(d)) : String(d)
        case .bool(let b): return b ? "Yes" : "No"
        case .array(let items):
            let parts = items.compactMap(\.text).filter { !$0.isEmpty }
            return parts.isEmpty ? nil : parts.joined(separator: ", ")
        case .null, .object: return nil
        }
    }

    var number: Int? {
        switch self {
        case .integer(let i): return i
        case .double(let d): return Int(d)
        case .string(let s): return Int(s)
        default: return nil
        }
    }

    var flag: Bool? {
        switch self {
        case .bool(let b): return b
        case .integer(let i): return i != 0
        case .string(let s): return s == "true"
        default: return nil
        }
    }
}

/// Decodes one row of a list without failing the whole list: a row that can't be read is
/// skipped (and the rest still show).
struct Lossy<T: Decodable>: Decodable {
    let value: T?
    init(from decoder: Decoder) throws {
        value = try? T(from: decoder)
    }
}

extension Array {
    func unwrapped<T>() -> [T] where Element == Lossy<T> { compactMap(\.value) }
}
