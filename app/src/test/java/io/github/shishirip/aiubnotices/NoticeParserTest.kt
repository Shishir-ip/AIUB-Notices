package io.github.shishirip.aiubnotices

import io.github.shishirip.aiubnotices.data.NoticeParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests against a saved copy of the real notice-board page structure
 * (app/src/test/resources/fixture.html, 3 notices).
 */
class NoticeParserTest {

    private fun fixtureHtml(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixture.html")
            ?: error("fixture.html not found on the test classpath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `parses all three notices from the fixture`() {
        val notices = NoticeParser.parse(fixtureHtml())
        assertEquals(3, notices.size)
    }

    @Test
    fun `extracts title, description, date and absolute url`() {
        val first = NoticeParser.parse(fixtureHtml()).first()
        assertEquals(
            "Spring 2026 Tuition Waiver & Financial Aid — Final Round",
            first.title,
        )
        assertEquals(
            "Applications for the final round of tuition waivers are open until 15 Nov 2026.",
            first.description,
        )
        assertEquals("04 Nov 2026", first.dateText)
        assertEquals("https://www.aiub.edu/notice/spring-2026-tuition-waiver", first.url)
    }

    @Test
    fun `unescapes html entities via jsoup text`() {
        val notices = NoticeParser.parse(fixtureHtml())
        assertTrue(notices[0].title.contains(" & "))
        assertTrue(notices[2].description.contains("–"))
        assertTrue(!notices[0].title.contains("&amp;"))
    }

    @Test
    fun `uses current year when the date year span is missing`() {
        val second = NoticeParser.parse(fixtureHtml())[1]
        assertEquals("28 Oct ${LocalDate.now().year}", second.dateText)
    }

    @Test
    fun `resolves relative hrefs against the site base url`() {
        val third = NoticeParser.parse(fixtureHtml())[2]
        assertEquals("https://www.aiub.edu/notice/library-hours-extended", third.url)
    }

    @Test
    fun `skips cards without an anchor link instead of crashing`() {
        val html = "<div class=\"notification\"><h2 class=\"title\">Orphan notice</h2></div>"
        assertTrue(NoticeParser.parse(html).isEmpty())
    }

    @Test
    fun `returns empty list for empty or garbage html`() {
        assertTrue(NoticeParser.parse("").isEmpty())
        assertTrue(NoticeParser.parse("<html><body>502 Bad Gateway</body></html>").isEmpty())
    }

    @Test
    fun `normalizeDate falls back to the provided year`() {
        assertEquals("04 Nov 2026", NoticeParser.normalizeDate("04 Nov 2026", "2026", 2000))
        assertEquals("04 Nov 2026", NoticeParser.normalizeDate("04 Nov", null, 2026))
        assertEquals("04 Nov 2026", NoticeParser.normalizeDate("04 Nov", "abc", 2026))
    }
}
