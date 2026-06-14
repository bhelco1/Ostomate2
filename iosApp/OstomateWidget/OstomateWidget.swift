import WidgetKit
import SwiftUI

// MARK: - Data model

struct SupplyEntry: TimelineEntry {
    let date: Date
    let rows: [WidgetRow]
}

struct WidgetRow: Identifiable {
    let id: Int64
    let name: String
    let deepLinkItem: String // "bag", "flange", or "id:<n>"
    let onHand: Int
    let daysRemaining: Double?

    var daysText: String {
        guard let d = daysRemaining else { return NSLocalizedString("widget_no_data", comment: "") }
        if d == 0 { return "0d left" }
        return "~\(Int(d.rounded()))d left"
    }

    var logURL: URL {
        URL(string: "ostimate://log?item=\(deepLinkItem)")!
    }
}

// MARK: - Provider
// NOTE: Real data requires an App Group shared container so the widget extension
// can read the same Room/SQLite database as the main app. Steps:
//   1. Enable App Groups capability on both targets (same group ID, e.g. "group.com.ostimate.app").
//   2. Configure Room's SQLite path to live in the shared container.
//   3. Open the DB here using SQLite / GRDB / or re-expose via a shared framework.
// Until then, the widget shows placeholder data.

struct OstimateProvider: TimelineProvider {
    func placeholder(in context: Context) -> SupplyEntry {
        SupplyEntry(date: Date(), rows: placeholderRows())
    }

    func getSnapshot(in context: Context, completion: @escaping (SupplyEntry) -> Void) {
        completion(SupplyEntry(date: Date(), rows: placeholderRows()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SupplyEntry>) -> Void) {
        let entry = SupplyEntry(date: Date(), rows: placeholderRows())
        let nextRefresh = Calendar.current.date(byAdding: .minute, value: 30, to: Date())!
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }

    private func placeholderRows() -> [WidgetRow] {
        [
            WidgetRow(id: 1, name: "Bag", deepLinkItem: "bag", onHand: 14, daysRemaining: 8.5),
            WidgetRow(id: 2, name: "Flange", deepLinkItem: "flange", onHand: 3, daysRemaining: 12.0),
        ]
    }
}

// MARK: - Views

struct OstimateWidgetEntryView: View {
    var entry: OstimateProvider.Entry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Ostimate")
                .font(.caption.bold())
                .foregroundStyle(.blue)

            ForEach(entry.rows) { row in
                Link(destination: row.logURL) {
                    HStack {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(row.name)
                                .font(.caption.weight(.medium))
                                .lineLimit(1)
                            Text(row.daysText)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("Log")
                            .font(.caption2.bold())
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(.blue.opacity(0.15), in: RoundedRectangle(cornerRadius: 4))
                            .foregroundStyle(.blue)
                    }
                }
            }

            Spacer(minLength: 0)
        }
        .padding(10)
    }
}

// MARK: - Widget

struct OstimateWidget: Widget {
    let kind = "OstimateWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: OstimateProvider()) { entry in
            OstimateWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Ostimate")
        .description("Days remaining and quick log for your ostomy supplies.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Preview

#Preview(as: .systemMedium) {
    OstimateWidget()
} timeline: {
    SupplyEntry(
        date: Date(),
        rows: [
            WidgetRow(id: 1, name: "Bag", deepLinkItem: "bag", onHand: 14, daysRemaining: 8.5),
            WidgetRow(id: 2, name: "Flange", deepLinkItem: "flange", onHand: 3, daysRemaining: 12.0),
        ]
    )
}
