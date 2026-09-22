package com.notesescape.sdocx

import com.notesescape.sdocx.core.SourceNameRules

internal object SdocxImportPolicy {
    const val samsungNotesMimeType = "application/sdoc"

    val pickerMimeTypes = arrayOf(
        "application/zip",
        "application/octet-stream",
        samsungNotesMimeType
    )

    fun acceptsPickerSelection(displayName: String?): Boolean = SourceNameRules.isSdocx(displayName)
}
