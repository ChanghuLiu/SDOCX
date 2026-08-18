package com.notesescape.sdocx

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.notesescape.sdocx.core.SourceNameRules
import com.notesescape.sdocx.export.SourceLocation

data class SafSource(val uri: Uri, val displayName: String, val relativeDirectory: List<String> = emptyList()) {
    val location get() = SourceLocation(relativeDirectory, displayName)
}
object SafSourceEnumerator {
    fun enumerateTree(context: Context, treeUri: Uri): List<SafSource> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val found = mutableListOf<SafSource>()
        fun visit(directory: DocumentFile, relative: List<String>) {
            val children = try { directory.listFiles().toList().sortedWith(compareBy<DocumentFile> { (it.name ?: it.uri.toString()).lowercase() }.thenBy { it.uri.toString() }) } catch (_: SecurityException) { emptyList() } catch (_: Exception) { emptyList() }
            children.forEach { child -> try { if (child.isDirectory) visit(child, relative + (child.name ?: "Folder")) else if (SourceNameRules.isSdocx(child.name)) found += SafSource(child.uri, child.name ?: "note.sdocx", relative) } catch (_: SecurityException) { } catch (_: Exception) { } }
        }
        visit(root, emptyList())
        return found.sortedWith(compareBy<SafSource> { it.location.relativePath.lowercase() }.thenBy { it.uri.toString() })
    }
}
