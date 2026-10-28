package com.apoorvdarshan.calorietracker.data

import java.text.Normalizer

/**
 * Lowercased word lists precomputed once per exercise so library search does not
 * re-tokenize every item on each keystroke. Mirrors iOS `ExerciseSearchIndex`.
 */
class ExerciseSearchIndex(name: String, metadata: List<String>) {
    val nameWords: List<String> = ExerciseSearch.words(name)

    /**
     * The name words joined without separators, starting at each word in turn
     * ("cablechestpress", "chestpress", "press"), so phrase matches stay word-aligned
     * and "pushup" / "push up" / "push-up" all compare equal.
     */
    val nameSuffixes: List<String> = nameWords.indices.map { nameWords.drop(it).joinToString("") }

    /**
     * Equipment, category, and primary-muscle values with their words joined
     * ("lowerback", "bodyonly"). They only match a query that names one of them as a
     * whole ("cable", "lower back", or an alias such as "abs"), so free-text queries
     * such as "chest press" still need every word in the name. Instructions and
     * secondary muscles are never searched: they mention many unrelated words.
     */
    val metadataTerms: List<String> =
        metadata.map { ExerciseSearch.words(it).joinToString("") }.filter { it.isNotEmpty() }
}

/**
 * Tokenized AND search over exercise names, with optional query aliases and
 * relevance ranking: exact name > name prefix > phrase inside the name >
 * every word in the name > query that names a muscle group, equipment, or category >
 * every word somewhere in the Spanish or English name (mixed languages, "squat barra") >
 * the same with small typos ("sentadila", "mancuerma"), see [TYPO_RANK].
 */
object ExerciseSearch {
    /** Rank of matches that need a typo fixed; shown only when nothing matches as typed. */
    const val TYPO_RANK = 6

    private val aliases = mapOf(
        "cable pushdown" to "ed_0201",
        "triceps cable pushdown" to "ed_0201",
        "tricep cable pushdown" to "ed_0201",
        "tricep pushdown" to "ed_0201",
        // The catalog's plain Bulgarian split squat (rear foot on a bench, dumbbells).
        "bulgarian split squat" to "ed_0410",
        "bulgarian squat" to "ed_0410",
        "sentadilla bulgara" to "ed_0410",
    )

    /**
     * Gym names that never appear in catalog titles, swapped for a word that does, so
     * "bulgarian split squat smith" still finds "Smith Single-Leg Split Squat".
     */
    private val tokenSynonyms = mapOf(
        "bulgarian" to "split",
    )

    /**
     * Gym shorthand that is not a prefix of the catalog value it means, mapped to
     * [ExerciseSearchIndex.metadataTerms] values. Applies only to the whole query.
     * Mirrors iOS `ExerciseSearchMatcher.metadataAliases`.
     */
    private val metadataAliases = mapOf(
        "abs" to listOf("abdominals"),
        "core" to listOf("abdominals"),
        "pec" to listOf("chest"),
        "pecs" to listOf("chest"),
        "pectoral" to listOf("chest"),
        "pectorals" to listOf("chest"),
        "delt" to listOf("shoulders"),
        "delts" to listOf("shoulders"),
        "deltoid" to listOf("shoulders"),
        "deltoids" to listOf("shoulders"),
        "quads" to listOf("quadriceps"),
        "hams" to listOf("hamstrings"),
        "calf" to listOf("calves"),
        "trapezius" to listOf("traps"),
        "back" to listOf("lats", "middleback", "lowerback"),
        "bodyweight" to listOf("bodyonly"),
    )

    // Marks stay inside words so Devanagari vowel signs and combining accents do not split
    // them (Swift's Character-based split already keeps those graphemes whole).
    private val wordSeparators = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    // Latin accents only, so "bulgara" finds "búlgara"; other scripts keep their marks.
    private val latinAccents = Regex("[\\u0300-\\u036f]")

    /** Lowercase without Latin accents ("Búlgara" → "bulgara"). */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(latinAccents, "")
            .let { Normalizer.normalize(it, Normalizer.Form.NFC) }

    /** Lowercased runs of letters and digits; punctuation and whitespace separate words. */
    fun words(text: String): List<String> =
        fold(text).split(wordSeparators).filter { it.isNotEmpty() }

    fun tokens(query: String): List<String> = words(query)

    /**
     * Relevance rank for [query] (lower is better), or null when the item does not match.
     * An empty query matches everything with rank 0.
     */
    fun relevance(index: ExerciseSearchIndex, query: String, exerciseId: String): Int? {
        val rawTokens = tokens(query)
        if (rawTokens.isEmpty()) return 0
        if (aliases[rawTokens.joinToString(" ")] == exerciseId) return 0
        val queryTokens = rawTokens.map { tokenSynonyms[it] ?: it }

        val compactQuery = queryTokens.joinToString("")
        val compactName = index.nameSuffixes.firstOrNull().orEmpty()
        if (compactName == compactQuery) return 0
        if (compactName.startsWith(compactQuery)) return 1
        if (index.nameSuffixes.drop(1).any { it.startsWith(compactQuery) }) return 2
        if (queryTokens.all { token -> index.nameWords.any { wordMatches(it, token) } }) return 3
        val aliasTerms = metadataAliases[queryTokens.joinToString(" ")].orEmpty()
        if (index.metadataTerms.any { it.startsWith(compactQuery) || it in aliasTerms }) return 4
        return null
    }

    fun relevance(item: ExerciseItem, query: String): Int? {
        val shown = relevance(item.searchIndex, query, item.id)
        val english = item.englishSearchIndex?.let { relevance(it, query, item.id) }
        return listOfNotNull(shown, english).minOrNull() ?: looseRelevance(item, query)
    }

    /**
     * Connecting words ("sentadilla de barra", "press with dumbbells") that names may
     * leave out; dropped from the query unless it is made only of them.
     */
    private val stopWords = setOf(
        "a", "al", "con", "de", "del", "el", "en", "la", "las", "los", "para", "por", "sobre", "un", "una", "y",
        "and", "for", "of", "on", "the", "to", "with"
    )

    /**
     * Last-resort tiers: every query word matches some word of the Spanish or English
     * name (5), or does so allowing typos ([TYPO_RANK]).
     */
    private fun looseRelevance(item: ExerciseItem, query: String): Int? {
        val tokens = tokens(query).map { tokenSynonyms[it] ?: it }
            .let { all -> all.filterNot { it in stopWords }.ifEmpty { all } }
        if (tokens.isEmpty()) return null
        val words = (item.searchIndex.nameWords + item.englishSearchIndex?.nameWords.orEmpty()).distinct()
        if (tokens.all { token -> words.any { wordMatches(it, token) } }) return 5
        if (tokens.all { token -> words.any { wordMatches(it, token) || closeTo(it, token) } }) return TYPO_RANK
        return null
    }

    /**
     * Whether [token] is a slightly misspelled start of [word]: one edit for tokens of
     * 4–7 letters, two from 8. Shorter tokens must be exact, or "cur" would find "bar".
     */
    private fun closeTo(word: String, token: String): Boolean {
        val allowed = when {
            token.length >= 8 -> 2
            token.length >= 4 -> 1
            else -> return false
        }
        if (word.length < token.length - allowed) return false
        val lengths = (token.length - allowed)..(token.length + allowed)
        return lengths.any { length -> length <= word.length && editDistance(word.take(length), token) <= allowed }
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    fun matches(item: ExerciseItem, query: String): Boolean = relevance(item, query) != null

    /**
     * Word-prefix match, so "tricep" finds "Triceps" but "press" no longer matches
     * inside unrelated words. Non-ASCII tokens also match inside a word because
     * scripts such as Japanese or Chinese do not separate words with spaces.
     * Plural tokens also match the singular word ("abs" finds "Ab", "curls" finds "Curl").
     */
    private fun wordMatches(word: String, token: String): Boolean {
        if (word.startsWith(token)) return true
        if (token.any { it.code > 127 }) return word.contains(token)
        if (token.length < 3 || !token.endsWith("s")) return false
        return word == token.dropLast(1) || (token.endsWith("es") && word == token.dropLast(2))
    }
}
