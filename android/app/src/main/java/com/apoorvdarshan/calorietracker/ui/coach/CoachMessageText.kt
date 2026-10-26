package com.apoorvdarshan.calorietracker.ui.coach

/**
 * Plain text of a chat message for Copy and "Send to food log": the Markdown Coach replies use
 * (headings, bold, italics, code, links, bullets, quotes, rules, tables) is removed and the
 * words are kept. Pure so it is unit-tested.
 */
object CoachMessageText {
    private val FENCE = Regex("""^\s*```.*$""")
    private val HEADING = Regex("""^\s{0,3}#{1,6}\s+""")
    private val QUOTE = Regex("""^\s{0,3}>\s?""")
    private val BULLET = Regex("""^(\s*)[-*+•]\s+""")
    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val TABLE_DIVIDER = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\(([^)]*)\)""")
    private val BOLD = Regex("""(\*\*|__)(.+?)\1""")
    private val ITALIC_STAR = Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""")
    private val ITALIC_UNDERSCORE = Regex("""(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])""")
    private val STRIKE = Regex("""~~(.+?)~~""")
    private val CODE = Regex("""`([^`]+)`""")

    fun plain(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").lines().mapNotNull { raw ->
            if (FENCE.matches(raw) || RULE.matches(raw) || TABLE_DIVIDER.matches(raw)) return@mapNotNull null
            var line = raw.trimEnd()
            line = HEADING.replace(line, "")
            line = QUOTE.replace(line, "")
            line = BULLET.replace(line) { "${it.groupValues[1]}- " }
            if (line.trimStart().startsWith("|") && line.trimEnd().endsWith("|")) {
                line = line.trim().trim('|').split('|').joinToString(" · ") { it.trim() }
            }
            inline(line)
        }
        return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun inline(text: String): String {
        var line = text
        line = IMAGE.replace(line) { it.groupValues[1] }
        line = LINK.replace(line) { match ->
            val label = match.groupValues[1]
            val url = match.groupValues[2]
            if (url.isBlank() || url == label) label else "$label ($url)"
        }
        line = CODE.replace(line) { it.groupValues[1] }
        line = BOLD.replace(line) { it.groupValues[2] }
        line = STRIKE.replace(line) { it.groupValues[1] }
        line = ITALIC_STAR.replace(line) { it.groupValues[1] }
        line = ITALIC_UNDERSCORE.replace(line) { it.groupValues[1] }
        return line
    }
}
