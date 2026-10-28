package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.CoachLogCollector
import com.apoorvdarshan.calorietracker.models.CoachLogItem
import com.apoorvdarshan.calorietracker.models.CoachLogMacros
import com.apoorvdarshan.calorietracker.models.CoachLogProposal
import com.apoorvdarshan.calorietracker.models.MealSchedule
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.Soreness
import com.apoorvdarshan.calorietracker.services.voice.VoiceArguments
import com.google.gson.GsonBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Coach chat's `propose_log_entries` tool: the model lists what the user ate, drank, weighed or
 * trained, and the chat shows one card the user must confirm. Parsing here is pure (no Android,
 * no I/O) so it is unit-tested; writing goes through [CoachLogService].
 *
 * Providers without tool calling (the on-device model) get the same items through a
 * `<fudai-log>{...}</fudai-log>` block in the reply instead ([extractFallbackBlock]).
 */
object CoachLogTool {
    const val TOOL_NAME = "propose_log_entries"

    const val DESCRIPTION = "Propose logging things the user says they ate, drank, weighed or trained into the app's diary: " +
        "foods and drinks with your calorie/macro estimate, plain water, body weight, body-fat percent, strength workouts, and timed cardio. " +
        "Use it whenever the user asks you to log, record, add, track or save something (\"regístralo\", \"anótalo\", \"log this\"), " +
        "or clearly reports a meal and wants it in the diary. Steps they walked that the phone or watch did not count go in a steps item " +
        "(a walk with minutes and steps: a cardio item plus a steps item; a cardio item without minutes is fine when it has distance_km or steps). " +
        "When the user gives a day total from their watch (\"6000 pasos según mi reloj\"), compare it with the steps the app already counted that day " +
        "(Steps section of the context) and add only the missing difference, or nothing if it is already counted; sleep (bed and wake time, or hours) goes in a sleep item. " +
        "When they say a muscle is still sore or not recovered (\"sigo muerto de los bíceps\"), add a soreness item so the app keeps it locked longer. " +
        "Never say you cannot update steps, sleep or soreness. Put every item in ONE call: one food item per distinct dish or drink " +
        "(keep quantities in description, e.g. \"2 ham and cheese sandwiches\"), with meal = breakfast, lunch, dinner or snack when the user says it. " +
        "Plain water goes in a water item, never as food; other drinks (mate, coffee, juice) are food items. " +
        "This does NOT save anything: the app re-estimates foods with its food analyzer and shows the user a card with Log, Edit and Cancel; " +
        "it is saved only if they tap Log. Afterwards tell the user to review the card; never say it was logged."

    private const val MAX_TEXT = 300
    private const val MAX_WATER_ML = 5_000
    private const val MAX_FOOD_KCAL = 10_000
    private const val MAX_MACRO_G = 1_000.0
    private const val MAX_PAST_DAYS = 30L
    private const val MAX_STEPS = 100_000

    fun parameterSchema(): Map<String, Any> {
        val item = linkedMapOf(
            "type" to "object",
            "properties" to linkedMapOf(
                "type" to prop("string", "One of: food, water, weight, body_fat, workout, cardio, steps, sleep, soreness"),
                "description" to prop("string", "food/workout: what the user said with quantities, e.g. \"2 ham and cheese sandwiches\" or \"3x10 bench press 60 kg\""),
                "name" to prop("string", "Short display name in the user's language, e.g. \"Sándwich de jamón y queso\" or \"Caminata\" (cardio activity)"),
                "serving" to prop("string", "food: portion, e.g. \"2 sandwiches\" or \"1.5 L\""),
                "meal" to prop("string", "food: breakfast, lunch, dinner or snack. Omit when the user did not say it; the app infers it from the time."),
                "date" to prop("string", "Optional ISO date yyyy-MM-dd, today or up to 30 days ago (default today)"),
                "time" to prop("string", "Optional time HH:mm (24-hour) when the user said it"),
                "calories" to prop("number", "food: your kcal estimate for the whole portion; cardio: kcal burned only if the user said it"),
                "protein_g" to prop("number", "food: protein grams estimate"),
                "carbs_g" to prop("number", "food: carbohydrate grams estimate"),
                "fat_g" to prop("number", "food: fat grams estimate"),
                "amount" to prop("number", "water: amount in unit"),
                "unit" to prop("string", "water: ml, l, oz, cup or glass; weight: kg or lb"),
                "value" to prop("number", "weight: body weight in unit"),
                "percent" to prop("number", "body_fat: percent, e.g. 18.5"),
                "minutes" to prop("number", "cardio: duration in minutes. Optional when distance_km or steps is given (the app estimates it)"),
                "distance_km" to prop("number", "cardio: distance in km, when the user or their watch says it"),
                "steps" to prop("number", "steps: how many steps to add to that day (a walk the phone or watch missed); cardio: the walk's steps, only to estimate minutes"),
                "bed_time" to prop("string", "sleep: when they went to bed, HH:mm"),
                "wake_time" to prop("string", "sleep: when they woke up, HH:mm; date is the morning they woke up"),
                "hours" to prop("number", "sleep: hours slept, when they did not give the times"),
                "muscle" to prop("string", "soreness: chest, back, shoulders, biceps, triceps, legs, hamstrings, glutes, calves or abs"),
                "extra_hours" to prop("number", "soreness: extra rest before training it again, 6–96 (default 24; 48 when they are very sore)")
            ),
            "required" to listOf("type")
        )
        return linkedMapOf(
            "type" to "object",
            "properties" to linkedMapOf(
                "items" to linkedMapOf(
                    "type" to "array",
                    "description" to "Everything to log, at most ${CoachLogCollector.MAX_ITEMS} items",
                    "items" to item
                )
            ),
            "required" to listOf("items")
        )
    }

    private fun formatKm(km: Double): String =
        if (km == Math.floor(km)) km.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", km)

    private fun prop(type: String, description: String) = linkedMapOf("type" to type, "description" to description)

    /** Result of reading the tool's arguments. */
    data class Parsed(val items: List<CoachLogItem>, val errors: List<String>)

    /** Reads raw tool arguments (plain maps/lists/numbers/strings) into card items. */
    fun parse(args: Map<String, Any?>, today: LocalDate): Parsed {
        val rawItems = when (val value = args["items"]) {
            is List<*> -> value
            is Map<*, *> -> listOf(value)
            else -> emptyList()
        }
        val items = mutableListOf<CoachLogItem>()
        val errors = mutableListOf<String>()
        rawItems.take(CoachLogCollector.MAX_ITEMS).forEachIndexed { index, raw ->
            val map = (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
            if (map == null) {
                errors += "items[$index] must be an object."
                return@forEachIndexed
            }
            try {
                items += parseItem(map, today)
            } catch (error: IllegalArgumentException) {
                errors += "items[$index]: ${error.message}"
            }
        }
        if (rawItems.size > CoachLogCollector.MAX_ITEMS) {
            errors += "Only the first ${CoachLogCollector.MAX_ITEMS} items were kept."
        }
        return Parsed(items, errors)
    }

    private fun parseItem(map: Map<String, Any?>, today: LocalDate): CoachLogItem {
        val kind = CoachLogItem.Kind.fromWire(text(map["type"]))
            ?: throw IllegalArgumentException("type must be food, water, weight, body_fat, workout, cardio, steps, sleep or soreness.")
        val date = date(map["date"], today)
        val time = text(map["time"]).takeIf { it.isNotEmpty() }?.let {
            VoiceArguments.parseTime(it)?.let(VoiceArguments::formatTime)
                ?: throw IllegalArgumentException("time must be HH:mm.")
        }
        val description = text(map["description"]).take(MAX_TEXT)
        val name = text(map["name"]).take(MAX_TEXT)
        val base = CoachLogItem(kind = kind, date = date.toString(), time = time, name = name, description = description)
        return when (kind) {
            CoachLogItem.Kind.FOOD -> {
                if (description.isEmpty() && name.isEmpty()) throw IllegalArgumentException("food needs a description.")
                val calories = number(map["calories"])
                val estimate = calories?.let {
                    if (it < 0 || it > MAX_FOOD_KCAL) throw IllegalArgumentException("calories must be 0 to $MAX_FOOD_KCAL.")
                    CoachLogMacros(
                        calories = Math.round(it).toInt(),
                        protein = macro(map["protein_g"]),
                        carbs = macro(map["carbs_g"]),
                        fat = macro(map["fat_g"])
                    )
                }
                base.copy(
                    description = description.ifEmpty { name },
                    serving = text(map["serving"]).take(MAX_TEXT).ifEmpty { null },
                    meal = text(map["meal"]).takeIf { it.isNotEmpty() }?.let {
                        parseMeal(it) ?: throw IllegalArgumentException("meal must be breakfast, lunch, dinner or snack.")
                    },
                    estimate = estimate
                )
            }
            CoachLogItem.Kind.WATER -> {
                val amount = number(map["amount"]) ?: throw IllegalArgumentException("water needs amount and unit.")
                val unit = text(map["unit"]).ifEmpty { "ml" }
                val ml = VoiceArguments.waterMilliliters(amount, normalizeUnit(unit))
                    ?: throw IllegalArgumentException("water unit must be ml, l, oz, cup or glass.")
                if (ml > MAX_WATER_ML) throw IllegalArgumentException("water must be at most $MAX_WATER_ML ml per item.")
                base.copy(waterMl = ml)
            }
            CoachLogItem.Kind.WEIGHT -> {
                val value = number(map["value"]) ?: number(map["amount"])
                    ?: throw IllegalArgumentException("weight needs value and unit.")
                val kg = VoiceArguments.weightKilograms(value, normalizeUnit(text(map["unit"]).ifEmpty { "kg" }))
                    ?: throw IllegalArgumentException("weight unit must be kg or lb.")
                if (kg < 20 || kg > 400) throw IllegalArgumentException("That weight looks wrong. Check the number and unit.")
                base.copy(weightKg = kg)
            }
            CoachLogItem.Kind.BODY_FAT -> {
                val percent = number(map["percent"]) ?: number(map["value"])
                    ?: throw IllegalArgumentException("body_fat needs percent.")
                if (percent < 2 || percent > 70) throw IllegalArgumentException("percent must be between 2 and 70.")
                base.copy(bodyFatPercent = percent)
            }
            CoachLogItem.Kind.WORKOUT -> {
                if (description.isEmpty()) throw IllegalArgumentException("workout needs a description.")
                if (date.isAfter(today)) throw IllegalArgumentException("workouts can only be logged for today or earlier.")
                base
            }
            CoachLogItem.Kind.CARDIO -> {
                val activity = name.ifEmpty { description }
                if (activity.isEmpty()) throw IllegalArgumentException("cardio needs a name (the activity).")
                val km = number(map["distance_km"])?.also {
                    if (it <= 0 || it > 500) throw IllegalArgumentException("distance_km must be above 0 and at most 500.")
                }
                val steps = number(map["steps"])?.takeIf { it > 0 }
                // A day of walking from the watch comes as steps or km, rarely minutes: estimate them
                // at an easy walk (about 100 steps or 1/12 km a minute) so the card can still be saved.
                val minutes = number(map["minutes"])?.let { Math.round(it).toInt() }
                    ?: km?.let { Math.round(it * 12).toInt().coerceAtLeast(1) }
                    ?: steps?.let { Math.round(it / 100).toInt().coerceAtLeast(1) }
                    ?: throw IllegalArgumentException("cardio needs minutes, distance_km or steps.")
                if (minutes !in 1..1_440) throw IllegalArgumentException("minutes must be 1 to 1440.")
                val burned = number(map["calories"])?.let { Math.round(it).toInt() }?.also {
                    if (it !in 1..5_000) throw IllegalArgumentException("calories must be 1 to 5000.")
                }
                val label = if (km == null || activity.contains("km", ignoreCase = true)) activity
                else "$activity · ${formatKm(km)} km"
                base.copy(name = label, minutes = minutes, caloriesBurned = burned)
            }
            CoachLogItem.Kind.STEPS -> {
                val steps = (number(map["steps"]) ?: number(map["value"]) ?: number(map["amount"]))?.let { Math.round(it).toInt() }
                    ?: throw IllegalArgumentException("steps needs the number of steps.")
                if (steps !in 1..MAX_STEPS) throw IllegalArgumentException("steps must be 1 to $MAX_STEPS.")
                base.copy(steps = steps)
            }
            CoachLogItem.Kind.SLEEP -> {
                val (start, end) = sleepWindow(map, date)
                base.copy(sleepStart = start.toString(), sleepEnd = end.toString())
            }
            CoachLogItem.Kind.SORENESS -> {
                val group = Soreness.groupFromName(text(map["muscle"]).ifEmpty { name })
                    ?: throw IllegalArgumentException("soreness needs muscle: chest, back, shoulders, biceps, triceps, legs, hamstrings, glutes, calves or abs.")
                val hours = number(map["extra_hours"])?.let { Math.round(it).toInt() } ?: Soreness.DEFAULT_EXTRA_HOURS
                base.copy(sorenessGroup = group.name, extraHours = hours.coerceIn(Soreness.MIN_EXTRA_HOURS, Soreness.MAX_EXTRA_HOURS))
            }
        }
    }

    /**
     * A night of sleep from bed_time / wake_time (HH:mm) or hours. [wakeDay] is the morning it
     * ended; a bed time later than the wake time was the evening before. Hours alone end at the
     * wake time given, else 07:00.
     */
    fun sleepWindow(map: Map<String, Any?>, wakeDay: LocalDate): Pair<java.time.LocalDateTime, java.time.LocalDateTime> {
        fun clock(key: String): LocalTime? = text(map[key]).takeIf { it.isNotEmpty() }?.let {
            VoiceArguments.parseTime(it) ?: throw IllegalArgumentException("$key must be HH:mm.")
        }
        val bed = clock("bed_time")
        val wake = clock("wake_time")
        val hours = number(map["hours"])
        val end = wakeDay.atTime(wake ?: LocalTime.of(7, 0))
        val start = when {
            bed != null -> {
                val sameDay = wakeDay.atTime(bed)
                if (sameDay.isBefore(end)) sameDay else wakeDay.minusDays(1).atTime(bed)
            }
            hours != null -> end.minusMinutes(Math.round(hours * 60))
            else -> throw IllegalArgumentException("sleep needs bed_time and wake_time, or hours.")
        }
        val minutes = java.time.Duration.between(start, end).toMinutes()
        if (minutes !in 15..(18 * 60)) throw IllegalArgumentException("sleep must last between 15 minutes and 18 hours.")
        return start to end
    }

    /** Spanish meal words too, in case the model echoes the user's language. */
    fun parseMeal(raw: String): MealType? {
        VoiceArguments.parseMeal(raw)?.let { return it }
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "desayuno" -> MealType.BREAKFAST
            "almuerzo", "comida" -> MealType.LUNCH
            "cena" -> MealType.DINNER
            "merienda", "colación", "colacion", "tentempié", "bocadillo" -> MealType.SNACK
            else -> null
        }
    }

    private fun normalizeUnit(unit: String): String = when (unit.trim().lowercase(Locale.ROOT)) {
        "vaso", "vasos" -> "glass"
        "taza", "tazas" -> "cup"
        "litro", "litros" -> "l"
        "mililitros" -> "ml"
        "kilo", "kilos" -> "kg"
        "libra", "libras" -> "lb"
        else -> unit
    }

    private fun date(value: Any?, today: LocalDate): LocalDate {
        val raw = text(value)
        if (raw.isEmpty()) return today
        val day = VoiceArguments.parseDate(raw, today)
            ?: throw IllegalArgumentException("date must be yyyy-MM-dd.")
        if (day.isAfter(today)) throw IllegalArgumentException("date cannot be in the future.")
        if (ChronoUnit.DAYS.between(day, today) > MAX_PAST_DAYS) {
            throw IllegalArgumentException("date must be within $MAX_PAST_DAYS days of today.")
        }
        return day
    }

    private fun text(value: Any?): String = when (value) {
        null -> ""
        is String -> value.trim()
        is Number, is Boolean -> value.toString()
        else -> ""
    }

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    private fun macro(value: Any?): Double {
        val grams = number(value) ?: return 0.0
        if (grams < 0 || grams > MAX_MACRO_G) throw IllegalArgumentException("macros must be 0 to ${MAX_MACRO_G.toInt()} g.")
        return grams
    }

    /** Runs the tool: validates, keeps the card and tells the model what the user will see. */
    fun propose(args: Map<String, Any?>, today: LocalDate, collector: CoachLogCollector?): String {
        collector ?: return errorJson("Logging cards are only available in Coach chat.")
        val parsed = parse(args, today)
        if (parsed.items.isEmpty()) {
            return errorJson(
                (listOf("Nothing to log.") + parsed.errors).joinToString(" ")
            )
        }
        val proposal = CoachLogProposal(items = parsed.items)
        val stored = collector.add(proposal)
            ?: return errorJson("This reply already has ${CoachLogCollector.MAX_PROPOSALS} log cards. Ask the user to review them first.")
        val items = stored.items.map { item ->
            linkedMapOf<String, Any?>(
                "type" to item.kind.wire,
                "name" to item.displayName,
                "date" to item.date
            ).apply {
                item.meal?.let { put("meal", VoiceArguments.mealWireValue(it)) }
                item.waterMl?.let { put("water_ml", it) }
                item.weightKg?.let { put("kg", Math.round(it * 10) / 10.0) }
                item.bodyFatPercent?.let { put("percent", it) }
                item.minutes?.let { put("minutes", it) }
            }
        }
        val result = linkedMapOf<String, Any?>(
            "proposal_id" to stored.id,
            "status" to "pending_user_confirmation",
            "saved" to false,
            "items" to items
        )
        if (parsed.errors.isNotEmpty()) result["skipped"] = parsed.errors
        result["note"] = "Nothing was saved yet. The app is estimating each food with its food analyzer and shows the user a card with " +
            "Log, Edit and Cancel under your reply; it is saved only if they tap Log. Briefly tell them to review the card. " +
            "Never say it was logged."
        return GSON.toJson(result)
    }

    private fun errorJson(message: String): String = GSON.toJson(linkedMapOf("error" to message))

    private val GSON = GsonBuilder().disableHtmlEscaping().create()

    // MARK: - Fallback block for providers without tool calling

    private val BLOCK = Regex("""<fudai-log>\s*(\{.*?\})\s*</fudai-log>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val FENCED_BLOCK = Regex("""```\s*fudai-log\s*(\{.*?\})\s*```""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    /** The system-prompt lines that teach a tool-less model to emit the block. */
    fun fallbackInstructions(): List<String> = listOf(
        "## Logging",
        "You CAN log foods, drinks, water, weight, body fat and workouts for the user. When they ask you to log something " +
            "(\"regístralo\", \"log this\"), add ONE block at the very end of your reply, exactly like:",
        "<fudai-log>{\"items\":[{\"type\":\"food\",\"description\":\"2 ham and cheese sandwiches\",\"name\":\"Sándwich de jamón y queso\",\"meal\":\"breakfast\",\"calories\":640,\"protein_g\":34,\"carbs_g\":60,\"fat_g\":28},{\"type\":\"water\",\"amount\":3,\"unit\":\"glass\"}]}</fudai-log>",
        "Item types: food (description, name, meal, calories, protein_g, carbs_g, fat_g), water (amount, unit ml/l/glass/cup), " +
            "weight (value, unit kg/lb), body_fat (percent), workout (description), cardio (name, minutes). " +
            "The app shows the user a card to confirm; never say it was already logged."
    )

    /**
     * Strips a `<fudai-log>` block from [reply] and returns the cleaned text plus the block's
     * arguments as plain maps, or null arguments when there is no valid block.
     */
    fun extractFallbackBlock(reply: String): Pair<String, Map<String, Any?>?> {
        val match = BLOCK.find(reply) ?: FENCED_BLOCK.find(reply) ?: return reply to null
        val args = runCatching { Json.parseToJsonElement(match.groupValues[1]) as? JsonObject }.getOrNull()
            ?.let { plainJson(it) as? Map<String, Any?> }
        val cleaned = reply.removeRange(match.range).replace(Regex("\n{3,}"), "\n\n").trim()
        return cleaned to args
    }

    /** org.json → plain Kotlin maps and lists, so one parser serves every provider. */
    fun plain(obj: JSONObject): Map<String, Any?> =
        obj.keys().asSequence().associateWith { key -> plainValue(obj.opt(key)) }

    private fun plainValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> plain(value)
        is JSONArray -> List(value.length()) { plainValue(value.opt(it)) }
        else -> value
    }

    /** kotlinx JSON → plain Kotlin values (numbers as Double), for the fallback block. */
    @Suppress("UNCHECKED_CAST")
    fun plainJson(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> element.mapValues { (_, value) -> plainJson(value) }
        is JsonArray -> element.map { plainJson(it) }
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            else -> element.doubleOrNull
        }
    }

    // MARK: - Timing

    /**
     * When a food is logged. An explicit time wins. Otherwise "now" when it is today and the
     * current time already falls in the requested meal, else a time inside that meal from the
     * user's meal schedule (never later than now), so Home shows it under the right meal.
     */
    fun timestamp(
        date: LocalDate,
        time: LocalTime?,
        meal: MealType?,
        now: Instant,
        zone: ZoneId,
        schedule: MealSchedule
    ): Instant {
        val nowLocal = now.atZone(zone)
        val today = nowLocal.toLocalDate()
        if (time != null) return clampToNow(date.atTime(time).atZone(zone).toInstant(), now)
        if (meal == null || meal == MealType.OTHER) {
            return if (date == today) now else date.atTime(nowLocal.toLocalTime()).atZone(zone).toInstant()
        }
        if (date == today && schedule.mealTypeAt(nowLocal.toLocalTime()) == meal) return now
        return clampToNow(date.atTime(representativeTime(meal, schedule)).atZone(zone).toInstant(), now)
    }

    /** The meal a food row lands in: the one the user named, else the schedule's meal at its log time. */
    fun inferredMeal(item: CoachLogItem, now: Instant, zone: ZoneId, schedule: MealSchedule): MealType {
        item.meal?.let { return it }
        val date = runCatching { LocalDate.parse(item.date) }.getOrElse { now.atZone(zone).toLocalDate() }
        val time = item.time?.let(VoiceArguments::parseTime)
        val at = timestamp(date, time, null, now, zone, schedule)
        return schedule.mealTypeAt(at.atZone(zone).toLocalTime())
    }

    /** Half an hour after the meal starts (snack: 15:30, between lunch and dinner). */
    fun representativeTime(meal: MealType, schedule: MealSchedule): LocalTime {
        val valid = schedule.validatedOrDefault()
        val minutes = when (meal) {
            MealType.BREAKFAST -> maxOf(valid.breakfastStartMinutes + 30, 7 * 60 + 30).coerceAtMost(valid.lunchStartMinutes - 1)
            MealType.LUNCH -> valid.lunchStartMinutes + 30
            MealType.DINNER -> valid.dinnerStartMinutes + 30
            MealType.SNACK, MealType.OTHER -> return LocalTime.of(15, 30)
        }.coerceIn(0, MealSchedule.MINUTES_PER_DAY - 1)
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    private fun clampToNow(candidate: Instant, now: Instant): Instant = if (candidate.isAfter(now)) now else candidate
}
