package com.notesescape.sdocx

import com.notesescape.sdocx.core.ParseStatus
import com.notesescape.sdocx.core.SdocxParser
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdocxImportPolicyTest {
    @Test
    fun pickerIncludesSamsungNotesMimeAndLegacyTypes() {
        assertTrue(SdocxImportPolicy.pickerMimeTypes.contains("application/sdoc"))
        assertTrue(SdocxImportPolicy.pickerMimeTypes.contains("application/zip"))
        assertTrue(SdocxImportPolicy.pickerMimeTypes.contains("application/octet-stream"))
    }

    @Test
    fun pickerSelectionRequiresSdocxExtension() {
        assertTrue(SdocxImportPolicy.acceptsPickerSelection("note.sdocx"))
        assertTrue(SdocxImportPolicy.acceptsPickerSelection("NOTE.SDOCX"))
        assertFalse(SdocxImportPolicy.acceptsPickerSelection("note.zip"))
        assertFalse(SdocxImportPolicy.acceptsPickerSelection("note.txt"))
        assertFalse(SdocxImportPolicy.acceptsPickerSelection(null))
    }

    @Test
    fun knownSdocxFixtureRemainsImportable() {
        val fixture = File("../test-fixtures/bxff-samsung-notes-format/sdocxFiles/Eg-walker.sdocx")
        assertTrue(fixture.isFile)
        val parsed = fixture.inputStream().use(SdocxParser::parse)
        assertTrue(parsed.status != ParseStatus.CORRUPT)
        assertTrue(parsed.pages.isNotEmpty())
    }
}
