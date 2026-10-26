package com.apoorvdarshan.calorietracker.ui.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachMessageTextTest {
    @Test
    fun stripsCoachMarkdownButKeepsTheWords() {
        val reply = """
            ## Tu desayuno

            **2 sándwiches** de jamón y queso: ~640 kcal.
            * Proteína: *34 g*
            - Grasa: __28 g__
            > Consejo: usa `pan integral`.

            ---
            Más info en [la guía](https://fud-ai.app/guia).
        """.trimIndent()
        assertEquals(
            """
            Tu desayuno

            2 sándwiches de jamón y queso: ~640 kcal.
            - Proteína: 34 g
            - Grasa: 28 g
            Consejo: usa pan integral.

            Más info en la guía (https://fud-ai.app/guia).
            """.trimIndent(),
            CoachMessageText.plain(reply)
        )
    }

    @Test
    fun keepsPlainTextAndSnakeCaseUntouched() {
        assertEquals("2 sándwiches y 1.5 L de mate", CoachMessageText.plain("2 sándwiches y 1.5 L de mate"))
        assertEquals("use protein_g and carbs_g", CoachMessageText.plain("use protein_g and carbs_g"))
        assertEquals("5 * 3 = 15", CoachMessageText.plain("5 * 3 = 15"))
    }

    @Test
    fun flattensTablesAndCodeFences() {
        val table = "| Comida | kcal |\n|---|---|\n| Desayuno | 640 |\n```\ncode\n```"
        assertEquals("Comida · kcal\nDesayuno · 640\ncode", CoachMessageText.plain(table))
    }
}
