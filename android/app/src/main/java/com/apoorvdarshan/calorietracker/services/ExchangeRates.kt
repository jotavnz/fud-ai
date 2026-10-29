package com.apoorvdarshan.calorietracker.services

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Bolívares per dollar for the pantry spending card, from DolarApi Venezuela
 * (ve.dolarapi.com, free, no key): the official BCV rate and the parallel average. Cached for
 * a few hours; the last good value is used offline.
 */
class ExchangeRates(context: Context) {
    data class Rate(val source: String, val bsPerUsd: Double, val updated: String)

    private val prefs = context.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    fun cached(): List<Rate> = parse(prefs.getString(KEY_JSON, null))

    suspend fun rates(maxAgeMillis: Long = 6 * 60 * 60 * 1000L): List<Rate> {
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (System.currentTimeMillis() - fetchedAt < maxAgeMillis) cached().takeIf { it.isNotEmpty() }?.let { return it }
        val body = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(URL).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrNull()
        }
        val fresh = parse(body)
        if (fresh.isNotEmpty()) {
            prefs.edit().putString(KEY_JSON, body).putLong(KEY_FETCHED_AT, System.currentTimeMillis()).apply()
            return fresh
        }
        return cached()
    }

    companion object {
        const val URL = "https://ve.dolarapi.com/v1/dolares"
        const val OFFICIAL = "oficial"
        private const val KEY_JSON = "json"
        private const val KEY_FETCHED_AT = "fetchedAt"

        fun parse(body: String?): List<Rate> = runCatching {
            Json.parseToJsonElement(body ?: return emptyList()).jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                val value = o["promedio"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                Rate(
                    source = o["fuente"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    bsPerUsd = value,
                    updated = o["fechaActualizacion"]?.jsonPrimitive?.contentOrNull.orEmpty().take(10)
                )
            }.filter { it.bsPerUsd > 0 }
        }.getOrDefault(emptyList())

        /** "Bs", "VES", "Bs." count as bolívares; "$", "USD", "US$" as dollars; anything else is left as is. */
        fun isBolivares(currency: String) = currency.trim().lowercase().trimEnd('.') in setOf("bs", "ves", "bss", "bsf", "bolivares", "bolívares")
        fun isDollars(currency: String) = currency.trim().lowercase() in setOf("$", "usd", "us$", "dolares", "dólares")

        /** Totals per currency shown in [target] ("Bs" or "$"); currencies that can't be converted are kept apart. */
        fun convert(totals: Map<String, Double>, target: String, rate: Double): Map<String, Double> {
            val out = linkedMapOf<String, Double>()
            totals.forEach { (currency, sum) ->
                val converted = when {
                    target == "Bs" && isDollars(currency) -> sum * rate
                    target == "$" && isBolivares(currency) -> sum / rate
                    target == "Bs" && isBolivares(currency) -> sum
                    target == "$" && isDollars(currency) -> sum
                    else -> null
                }
                if (converted == null) out[currency] = (out[currency] ?: 0.0) + sum
                else out[target] = (out[target] ?: 0.0) + converted
            }
            return out
        }
    }
}
