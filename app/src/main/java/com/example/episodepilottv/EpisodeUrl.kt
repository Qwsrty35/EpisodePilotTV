package com.example.episodepilottv

object EpisodeUrl {
    private val episodeRegex = Regex("(?i)(episode(?:[-_\\s]?))(\\d+(?:\\.\\d+)?)")

    fun episodeNumber(url: String): Double? =
        episodeRegex.find(url)?.groupValues?.getOrNull(2)?.toDoubleOrNull()

    fun shift(url: String, delta: Int): String? {
        val match = episodeRegex.find(url) ?: return null
        val original = match.groupValues[2]
        val number = original.toDoubleOrNull() ?: return null
        val shifted = number + delta
        if (shifted < 0) return null

        val formatted = if (original.contains('.')) {
            val decimals = original.substringAfter('.').length
            ("%." + decimals + "f").format(java.util.Locale.US, shifted)
        } else {
            shifted.toInt().toString()
        }

        val start = match.groups[2]!!.range.first
        val endInclusive = match.groups[2]!!.range.last
        return url.substring(0, start) + formatted + url.substring(endInclusive + 1)
    }
}
