package com.apoorvdarshan.calorietracker.models

/**
 * Tabs the user can hide from the bottom bar. Home and Settings are always shown.
 * Values are the tab routes, stored comma-separated ("coach,workouts"), the same format iOS uses.
 */
object TabBarSettings {
    const val STORAGE_KEY = "hiddenTabs"
    val Hideable = listOf("progress", "coach", "workouts")

    /** Unknown values (including Home and Settings) are ignored, so a missing key shows every tab. */
    fun decode(raw: String?): Set<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it in Hideable }.toSet()

    fun encode(hidden: Set<String>): String = Hideable.filter { it in hidden }.joinToString(",")

    const val ORDER_STORAGE_KEY = "tabOrder"
    val DefaultOrder = listOf("home", "progress", "workouts", "coach", "settings")

    /** The stored tab order; unknown routes are dropped and missing ones keep their default place at the end. */
    fun decodeOrder(raw: String?): List<String> {
        val stored = raw.orEmpty().split(',').map { it.trim() }.filter { it in DefaultOrder }.distinct()
        return stored + DefaultOrder.filterNot { it in stored }
    }

    fun encodeOrder(order: List<String>): String = decodeOrder(order.joinToString(",")).joinToString(",")

    /** [order] with [route] moved one place earlier (delta -1) or later (delta 1). */
    fun moved(order: List<String>, route: String, delta: Int): List<String> {
        val list = decodeOrder(order.joinToString(",")).toMutableList()
        val from = list.indexOf(route)
        val to = from + delta
        if (from < 0 || to !in list.indices) return list
        list.removeAt(from)
        list.add(to, route)
        return list
    }

    /**
     * The stored value after showing or hiding one tab. Applied to what is stored right now,
     * so the other tabs keep their current state even if a screen still shows an older set.
     */
    fun withTabHidden(raw: String?, route: String, hidden: Boolean): String {
        val current = decode(raw)
        if (route !in Hideable) return encode(current)
        return encode(if (hidden) current + route else current - route)
    }
}
