package io.github.shishirip.aiubnotices.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate

/**
 * Parses the server-rendered HTML of https://www.aiub.edu/category/notices.
 *
 * Each notice on the page is an anchor wrapping a `div.notification` card:
 *
 * ```html
 * <a href="/notice-slug">
 *   <div class="notification">
 *     <div class="date-custom ...">04 Nov <span>2026</span></div>
 *     <div class="notification-text">
 *       <h2 class="title">Notice title here ...</h2>
 *       <p class="desc">Short description</p>
 *       <a class="info-link" href="/notice-slug">Details</a>
 *     </div>
 *   </div>
 * </a>
 * ```
 *
 * The parser is deliberately defensive: anything it cannot fully understand
 * (missing title, missing link, broken markup) is skipped instead of thrown,
 * so a site redesign can never crash the app.
 */
object NoticeParser {

    const val BASE_URL: String = "https://www.aiub.edu"
    const val NOTICES_URL: String = "$BASE_URL/category/notices"

    private val whitespace = Regex("\\s+")

    /**
     * Parses notice cards out of the page HTML.
     *
     * @param html raw page source
     * @param fallbackYear used when the date block has no year `<span>`
     */
    fun parse(html: String, fallbackYear: Int = LocalDate.now().year): List<Notice> {
        val document = Jsoup.parse(html, BASE_URL)
        return document.select("div.notification").mapNotNull { element ->
            runCatching { parseOne(element, fallbackYear) }.getOrNull()
        }
    }

    private fun parseOne(element: Element, fallbackYear: Int): Notice? {
        val title = element.selectFirst("h2.title")?.text()?.trim().orEmpty()
        if (title.isEmpty()) return null

        val description = element.selectFirst("p.desc")?.text()?.trim().orEmpty()

        val dateBlock = element.selectFirst(".date-custom")
        val dateText = normalizeDate(
            raw = dateBlock?.text().orEmpty(),
            yearFromSpan = dateBlock?.selectFirst("span")?.text()?.trim(),
            fallbackYear = fallbackYear,
        )

        // The outer anchor is an ancestor of the card in the raw source, but the
        // HTML5 parser's "adoption agency" algorithm restructures nested anchors
        // (<a><div> ... <a>Details</a> ... </div></a>), so the wrapper anchor may
        // no longer be an ancestor of the card at parse time. Fall back to the
        // anchors inside the card — the "info-link" duplicates the same URL.
        val anchors = listOfNotNull(
            element.closest("a[href]"),
            element.selectFirst("a.info-link[href]"),
            element.selectFirst("a[href]"),
        )
        val href = anchors.firstNotNullOfOrNull { anchor ->
            anchor.attr("href").trim().takeIf { it.isNotEmpty() }
        }
        val url = when {
            href == null -> return null
            href.startsWith("http://") || href.startsWith("https://") -> href
            else -> BASE_URL + if (href.startsWith("/")) href else "/$href"
        }

        return Notice(title = title, description = description, dateText = dateText, url = url)
    }

    /**
     * Normalizes the date block text ("04 Nov 2026" / "04 Nov" when the year
     * span is absent) into a display string. Jsoup's `.text()` already
     * unescapes HTML entities such as `&amp;`.
     */
    fun normalizeDate(raw: String, yearFromSpan: String?, fallbackYear: Int): String {
        val parts = raw.split(whitespace).filter { it.isNotBlank() }
        val day = parts.getOrNull(0).orEmpty()
        val month = parts.getOrNull(1).orEmpty()
        val year = yearFromSpan?.takeIf { it.isNotBlank() && it.toIntOrNull() != null }
            ?: parts.getOrNull(2)?.takeIf { it.toIntOrNull() != null }
            ?: fallbackYear.toString()
        return listOf(day, month, year).filter { it.isNotBlank() }.joinToString(" ")
    }
}
