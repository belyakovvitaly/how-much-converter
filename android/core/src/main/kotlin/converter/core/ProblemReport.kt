// A report of a picture that did not convert as it should, written for the
// person who has to find out why.
//
// What makes a report worth reading is what the app made of the picture: the
// lines the recognizer returned, where it found them, which of them became
// prices, and the currencies and switches in force. "The tag was not read" is a
// puzzle; "read as 1648 at 0.91, box 212,340–388,392" is an afternoon.
package converter.core

import kotlin.math.roundToLong

/** Everything a report says, gathered by the app; [problemReportText] writes it out. */
data class ProblemReport(
    val appVersion: String,
    /** "Android 16 (API 36), samsung SM-S931B" — whatever the platform says. */
    val device: String,
    /** When it was written, already formatted by the caller: this layer has no clock or zone. */
    val writtenAt: String,
    /** Where the picture came from: "camera", "gallery", "shared". */
    val origin: String,
    /** The picture as it was read, which is not always as it was stored. */
    val readWidth: Int,
    val readHeight: Int,
    val source: String?,
    val detectedSource: String?,
    val target: String?,
    val detectedTarget: String?,
    val receipt: Boolean,
    val rates: RateTable?,
    /** When the rates were fetched, formatted by the caller, as [writtenAt] is. */
    val ratesFetchedAt: String?,
    /** When the service published those rates, formatted likewise; null if it did not say. */
    val ratesPublishedAt: String? = null,
    val lines: List<OcrLine>,
    val prices: List<LocatedPrice>,
    /** What the reader said was wrong, if anything. */
    val note: String,
)

fun problemReportText(report: ProblemReport): String = buildString {
    appendLine("How Much? ${report.appVersion} · ${report.device}")
    appendLine("written ${report.writtenAt}")
    appendLine()

    val note = report.note.trim()
    appendLine("What was wrong: ${note.ifEmpty { "(not said)" }}")
    appendLine()

    appendLine("picture: ${report.origin}, read at ${report.readWidth}x${report.readHeight}")
    appendLine(
        "prices in: ${report.source ?: "?"} (detected ${report.detectedSource ?: "nothing"})" +
            "  ·  convert into: ${report.target ?: "?"} (detected ${report.detectedTarget ?: "nothing"})" +
            "  ·  receipt: ${if (report.receipt) "on" else "off"}"
    )
    val rates = report.rates
    appendLine(
        if (rates == null) "rates: none"
        else "rates: ${rates.base}-based" +
            (report.ratesPublishedAt?.let { ", published $it" } ?: "") +
            ", fetched ${report.ratesFetchedAt ?: "at ${rates.fetchedAt} (epoch ms)"}" +
            pairRate(rates, report.source, report.target)
    )
    appendLine()

    appendLine("prices found (${report.prices.size}):")
    if (report.prices.isEmpty()) appendLine("  none")
    for (located in report.prices) {
        val price = located.price
        val converted = report.target?.let { rates?.convert(price.amount, price.code, it) }
        append("  ${price.amount} ${price.code}")
        if (converted != null && report.target != null) {
            append(" -> ${formatConverted(converted, report.target)}")
        }
        appendLine("  at ${located.box.text()}")
    }
    appendLine()

    appendLine("lines read (${report.lines.size}), as the recognizer returned them:")
    if (report.lines.isEmpty()) appendLine("  none")
    for (line in report.lines) {
        // Quoted, so a trailing space or a stray glyph is visible.
        val confidence = (line.confidence * 100).roundToLong() / 100.0
        appendLine("  \"${line.text}\"  ${confidence}  at ${line.box?.text() ?: "?"}")
    }
}

private fun pairRate(rates: RateTable, source: String?, target: String?): String {
    if (source == null || target == null) return ""
    val rate = rates.convert(1.0, source, target) ?: return ", no rate for $source -> $target"
    return ", 1 $source = $rate $target"
}

/** A box as whole pixels: left,top–right,bottom. */
private fun Box.text(): String =
    "${x0.roundToLong()},${y0.roundToLong()}–${x1.roundToLong()},${y1.roundToLong()}"
