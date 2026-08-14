package com.notesescape.sdocx

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class MainActivityStringAuditTest {
    @Test
    fun composeTextDoesNotReintroduceLiteralUserFacingStrings() {
        val source = File("src/main/java/com/notesescape/sdocx/MainActivity.kt").readText()
        assertFalse(Regex("Text\\s*\\(\\s*\\\"").containsMatchIn(source))
        assertFalse(Regex("label\\s*=\\s*\\{\\s*Text\\s*\\(\\s*\\\"").containsMatchIn(source))
    }
}
