package io.github.shishirip.aiubnotices.data

/** A single notice scraped from the AIUB notice board. */
data class Notice(
    val title: String,
    val description: String,
    /** Display date, e.g. "04 Nov 2026". */
    val dateText: String,
    /** Absolute URL of the notice page. */
    val url: String,
)
