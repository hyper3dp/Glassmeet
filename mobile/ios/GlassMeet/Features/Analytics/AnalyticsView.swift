import Charts
import SwiftUI

/// How guests book: totals, insights, bookings by month and the busiest times.
/// Counts follow the web app's Analytics page (cancelled meetings don't count as held).
struct AnalyticsView: View {
    @Environment(Session.self) private var session
    @Environment(AppData.self) private var data

    var body: some View {
        let stats = AnalyticsStats(bookings: data.bookings, eventTypes: data.eventTypes.count, zone: session.timeZone)

        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.l) {
                if data.bookings.isEmpty, data.bookingsState.isLoaded {
                    EmptyStateView(title: "No bookings yet", systemImage: "chart.bar",
                                   message: "Once guests book, you’ll see your busiest months and times here.")
                        .card()
                }

                let columns = [GridItem(.flexible(), spacing: Theme.Space.s), GridItem(.flexible(), spacing: Theme.Space.s)]
                LazyVGrid(columns: columns, spacing: Theme.Space.s) {
                    Tile(title: "Total booked", value: stats.total >= 1000 ? "1000+" : "\(stats.total)")
                    Tile(title: "Confirmed", value: "\(stats.confirmed)")
                    Tile(title: "Cancelled", value: "\(stats.cancelled)")
                    Tile(title: "Average length", value: "\(stats.averageLength) min")
                }

                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text("Insights").font(Theme.Typography.title3).foregroundStyle(Theme.text)
                    Insight(icon: "star", title: "Most popular event", value: stats.popularEvent ?? "Not enough data")
                    Divider()
                    Insight(icon: "clock", title: "Most popular time", value: stats.popularHour.map(Fmt.clockLabel) ?? "Not enough data")
                    Divider()
                    Insight(icon: "person.2", title: "Total attendees", value: "\(stats.attendees)")
                    Divider()
                    Insight(icon: "link", title: "Event types", value: "\(stats.eventTypes)")
                }
                .card()

                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text("Bookings by month").font(Theme.Typography.title3).foregroundStyle(Theme.text)
                    if stats.months.isEmpty {
                        Text("No meetings held yet.").font(.subheadline).foregroundStyle(Theme.textSecondary)
                    } else {
                        Chart(stats.months) { m in
                            BarMark(x: .value("Month", m.label), y: .value("Bookings", m.count))
                                .foregroundStyle(Theme.primary)
                                .cornerRadius(4)
                        }
                        .chartXScale(domain: stats.months.map(\.label))
                        .chartYAxis { AxisMarks(position: .leading) }
                        .frame(height: 200)
                    }
                }
                .card()

                VStack(alignment: .leading, spacing: Theme.Space.s) {
                    Text("Busiest times").font(Theme.Typography.title3).foregroundStyle(Theme.text)
                    Text("Meetings by weekday and start hour, 8 AM to 8 PM").font(.footnote).foregroundStyle(Theme.textSecondary)
                    Chart(stats.heat) { cell in
                        RectangleMark(x: .value("Hour", cell.hourLabel), y: .value("Day", cell.dayLabel), width: .ratio(0.9), height: .ratio(0.85))
                            .foregroundStyle(cell.count == 0 ? Theme.surfaceRaised : Theme.primary.opacity(0.25 + 0.75 * Double(cell.count) / Double(stats.maxHeat)))
                            .cornerRadius(3)
                    }
                    .chartXScale(domain: AnalyticsStats.hourLabels)
                    .chartYScale(domain: AnalyticsStats.dayLabels)
                    .chartXAxis {
                        AxisMarks(values: AnalyticsStats.hourLabels.enumerated().filter { $0.offset % 2 == 0 }.map(\.element))
                    }
                    .frame(height: 220)
                }
                .card()
            }
            .padding(.horizontal, Theme.Space.m)
            .padding(.bottom, Theme.Space.xxl)
        }
        .background(Theme.background)
        .overlay {
            if data.bookings.isEmpty, !data.bookingsState.isLoaded {
                if let error = data.bookingsState.error {
                    ErrorStateView(message: error) { await data.loadAll() }
                } else {
                    LoadingView()
                }
            }
        }
        .navigationTitle("Analytics")
        .refreshable { await data.loadAll() }
        .task { await data.loadAll() }
    }
}

private struct Tile: View {
    var title: String
    var value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value).font(Theme.Typography.stat).foregroundStyle(Theme.text).lineLimit(1).minimumScaleFactor(0.7)
            Text(title).font(.footnote).foregroundStyle(Theme.textSecondary)
        }
        .padding(Theme.Space.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface, in: .rect(cornerRadius: Theme.Radius.tile))
        .accessibilityElement(children: .combine)
    }
}

private struct Insight: View {
    var icon: String
    var title: String
    var value: String
    var body: some View {
        HStack(spacing: Theme.Space.s) {
            Image(systemName: icon).foregroundStyle(Theme.link).frame(width: 24)
            Text(title).font(.subheadline).foregroundStyle(Theme.textSecondary)
            Spacer(minLength: Theme.Space.xs)
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.text).multilineTextAlignment(.trailing)
        }
        .frame(minHeight: 32)
        .accessibilityElement(children: .combine)
    }
}

/// The numbers, in the host's time zone
struct AnalyticsStats {
    struct Month: Identifiable { var id: String; var label: String; var count: Int }
    struct Cell: Identifiable { var id: String { "\(dayLabel)-\(hourLabel)" }; var dayLabel: String; var hourLabel: String; var count: Int }

    static let dayLabels = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    static let hourLabels = ["8a", "9a", "10a", "11a", "12p", "1p", "2p", "3p", "4p", "5p", "6p", "7p"]

    var total = 0
    var confirmed = 0
    var cancelled = 0
    var averageLength = 0
    var popularEvent: String?
    /// Minutes since midnight of the top start hour, when one hour clearly leads
    var popularHour: Int?
    var attendees = 0
    var eventTypes = 0
    var months: [Month] = []
    var heat: [Cell] = []
    var maxHeat = 1

    init(bookings: [Booking], eventTypes: Int, zone: TimeZone) {
        let cal = Fmt.calendar(zone)
        let held = bookings.filter { !$0.isCancelled }
        total = bookings.count
        confirmed = bookings.filter { $0.status == "confirmed" && $0.approvalStatus != "pending" }.count
        cancelled = bookings.filter(\.isCancelled).count
        let lengths = held.map(\.lengthMinutes).filter { $0 > 0 }
        averageLength = lengths.isEmpty ? 0 : Int((Double(lengths.reduce(0, +)) / Double(lengths.count)).rounded())
        attendees = held.reduce(0) { $0 + ($1.attendeeCount ?? 1) }
        self.eventTypes = eventTypes

        var events: [String: Int] = [:]
        for b in held { events[b.eventTitle, default: 0] += 1 }
        popularEvent = events.max { $0.value < $1.value }?.key

        var hours: [Int: Int] = [:]
        var grid = Array(repeating: Array(repeating: 0, count: 12), count: 7)
        var monthCounts: [String: Int] = [:]
        for b in held {
            let c = cal.dateComponents([.weekday, .hour, .year, .month], from: b.start)
            let hour = c.hour ?? 0
            hours[hour, default: 0] += 1
            let day = (c.weekday ?? 1) - 1
            if (8..<20).contains(hour), (0..<7).contains(day) { grid[day][hour - 8] += 1 }
            monthCounts[String(format: "%04d-%02d", c.year ?? 0, c.month ?? 0), default: 0] += 1
        }
        let ranked = hours.sorted { $0.value > $1.value }
        if let top = ranked.first, top.value > 1, ranked.count < 2 || top.value > ranked[1].value { popularHour = top.key * 60 }

        var monthStyle = Date.FormatStyle().month(.abbreviated).year(.twoDigits)
        monthStyle.timeZone = zone
        months = monthCounts.keys.sorted().suffix(12).map { key in
            let parts = key.split(separator: "-").compactMap { Int($0) }
            let date = cal.date(from: DateComponents(year: parts.first, month: parts.last, day: 15)) ?? .now
            return Month(id: key, label: date.formatted(monthStyle), count: monthCounts[key] ?? 0)
        }

        var cells: [Cell] = []
        for day in 0..<7 {
            for h in 0..<12 {
                cells.append(Cell(dayLabel: Self.dayLabels[day], hourLabel: Self.hourLabels[h], count: grid[day][h]))
            }
        }
        heat = cells
        maxHeat = max(1, grid.flatMap { $0 }.max() ?? 1)
    }
}
