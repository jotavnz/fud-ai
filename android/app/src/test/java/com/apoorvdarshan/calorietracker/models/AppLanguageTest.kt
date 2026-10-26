package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun supportedTagsMatchShippedLocales() {
        assertEquals(listOf("en", "es"), AppLanguage.supportedTags)
    }

    @Test
    fun displayNameUsesNativeLocale() {
        assertEquals("English", AppLanguage.displayName("en"))
        assertEquals("Español", AppLanguage.displayName("es"))
    }

    @Test
    fun matchSupportedAcceptsExactAndLanguageOnlyTags() {
        assertNull(AppLanguage.matchSupported(null))
        assertNull(AppLanguage.matchSupported(""))
        assertEquals("es", AppLanguage.matchSupported("es"))
        assertEquals("es", AppLanguage.matchSupported("es-VE"))
        assertEquals("en", AppLanguage.matchSupported("en-US"))
        assertNull(AppLanguage.matchSupported("de"))
    }
}
