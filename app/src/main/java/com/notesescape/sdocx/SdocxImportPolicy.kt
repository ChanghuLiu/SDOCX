package com.notesescape.sdocx

import com.notesescape.sdocx.core.SourceNameRules

internal object SdocxImportPolicy {
    const val samsungNotesMimeType = "application/sdoc"

    // Providers disagree on SDOCX MIME types; validate the returned filename instead.
    val pickerMimeTypes = arrayOf("*/*")

    fun acceptsPickerSelection(displayName: String?): Boolean = SourceNameRules.isSdocx(displayName)
}
