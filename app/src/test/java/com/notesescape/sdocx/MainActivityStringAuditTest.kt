package com.notesescape.sdocx

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityStringAuditTest {
    @Test
    fun composeTextDoesNotReintroduceLiteralUserFacingStrings() {
        val source = File("src/main/java/com/notesescape/sdocx/MainActivity.kt").readText()
        assertFalse(Regex("Text\\s*\\(\\s*\\\"").containsMatchIn(source))
        assertFalse(Regex("label\\s*=\\s*\\{\\s*Text\\s*\\(\\s*\\\"").containsMatchIn(source))
    }

    @Test
    fun folderStructureIsInformationalAndObsidianResultIsExplicit() {
        val source = File("src/main/java/com/notesescape/sdocx/MainActivity.kt").readText()
        assertTrue(source.contains("FolderStructureInfo"))
        assertFalse(source.contains("Option(stringResource(R.string.preserve_folder_structure)"))
        assertTrue(source.contains("R.string.obsidian_vault_ready"))
        assertTrue(source.contains("R.string.folder_structure_unavailable"))
    }
}
