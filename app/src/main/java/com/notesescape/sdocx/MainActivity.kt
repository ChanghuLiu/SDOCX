package com.notesescape.sdocx

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.notesescape.sdocx.core.HandwritingElement
import com.notesescape.sdocx.core.ImageElement
import com.notesescape.sdocx.core.ParseResult
import com.notesescape.sdocx.core.ParseStatus
import com.notesescape.sdocx.core.RichTextElement
import com.notesescape.sdocx.core.SdocxParser
import com.notesescape.sdocx.export.ArchiveExporter
import com.notesescape.sdocx.export.ConversionSource
import com.notesescape.sdocx.export.ExportFormat
import com.notesescape.sdocx.export.ExportOptions
import com.notesescape.sdocx.export.ArchiveSummary
import com.notesescape.sdocx.export.NoteReport
import com.notesescape.sdocx.ui.theme.NotesEscapeSDOCXTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.io.FileOutputStream

private enum class UiStage { SELECT, PREFLIGHT, OPTIONS, CONVERTING, RESULT }

private data class PreflightSummary(
    val selected: Int = 0,
    val readable: Int = 0,
    val locked: Int = 0,
    val corrupt: Int = 0,
    val text: Int = 0,
    val handwriting: Int = 0,
    val media: Int = 0,
    val imagesMedia: Int = 0,
    val handwritingPages: Int = 0,
    val attachments: Int = 0,
    val folderPaths: Set<String> = emptySet(),
    val folderRows: Map<String, Int> = emptyMap()
)

private data class ConversionSummary(
    val completed: Int = 0,
    val partial: Int = 0,
    val locked: Int = 0,
    val corruptFailed: Int = 0,
    val preset: ExportFormat = ExportFormat.PORTABLE_MARKDOWN,
    val foldersPreserved: Int = 0,
    val imagesMedia: Int = 0,
    val handwritingPages: Int = 0,
    val attachments: Int = 0,
    val failed: Int = 0,
    val cancelled: Boolean = false,
    val savedFile: String? = null,
    val savedUri: Uri? = null
)

private data class ConversionProgress(
    val current: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val completed: Int = 0,
    val partial: Int = 0,
    val locked: Int = 0,
    val corruptFailed: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanConversionCache(this)
        setContent { NotesEscapeSDOCXTheme { NotesEscapeApp(intent) } }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotesEscapeApp(incoming: Intent) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(incomingSources(incoming)) }
    var folderImport by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(if (sources.isEmpty()) UiStage.SELECT else UiStage.PREFLIGHT) }
    var preflight by remember { mutableStateOf(PreflightSummary(sources.size)) }
    var progress by remember { mutableStateOf(ConversionProgress(total = sources.size)) }
    var result by remember { mutableStateOf(ConversionSummary()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ExportFormat.PORTABLE_MARKDOWN) }
    var preserve by remember { mutableStateOf(true) }
    var attachments by remember { mutableStateOf(true) }
    var originals by remember { mutableStateOf(true) }
    var metadata by remember { mutableStateOf(true) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    fun startPreflight(selected: List<SafSource>, fromFolder: Boolean = folderImport) {
        activeJob?.cancel()
        sources = selected
        folderImport = fromFolder
        preflight = PreflightSummary(selected = selected.size)
        progress = ConversionProgress(total = selected.size)
        errorMessage = null
        if (selected.isEmpty()) {
            stage = UiStage.SELECT
            return
        }
        stage = UiStage.PREFLIGHT
        activeJob = scope.launch {
            val cancellation = AtomicBoolean(false)
            val completion = coroutineContext[Job]?.invokeOnCompletion { cancellation.set(true) }
            try {
                selected.forEachIndexed { index, sourceInfo ->
                    ensureActive()
                    val source: ConversionSource = CachedSafSource(context.contentResolver, sourceInfo.uri, sourceInfo.displayName, conversionCacheDirectory(context), cancellation::get, sourceInfo.relativeDirectory)
                    val parsed = try {
                        source.use { it.openStream().use(SdocxParser::parse) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ParseResult(metadata = com.notesescape.sdocx.core.DocumentMetadata(), pages = emptyList(), media = emptyList(), status = ParseStatus.CORRUPT, warnings = listOf(com.notesescape.sdocx.core.ParseWarning(error.message ?: resources.getString(R.string.unknown_error))))
                    }
                    withContext(Dispatchers.Main) {
                        preflight = preflight.add(parsed, sourceInfo.relativeDirectory, folderImport)
                        progress = progress.copy(current = sourceInfo.displayName, index = index + 1)
                    }
                    parsed.media.forEach { it.close() }
                }
                withContext(Dispatchers.Main) { stage = UiStage.OPTIONS }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main) { stage = UiStage.SELECT }
            } finally {
                completion?.dispose()
            }
        }
    }

    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        startPreflight(uris.sortedBy(Uri::toString).map { SafSource(it, displayName(it)) }, fromFolder = false)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            activeJob?.cancel()
            stage = UiStage.PREFLIGHT
            activeJob = scope.launch(Dispatchers.IO) {
                val found = SafSourceEnumerator.enumerateTree(context, tree)
                withContext(Dispatchers.Main) { activeJob = null; startPreflight(found, fromFolder = true) }
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destination ->
        if (destination != null) {
            activeJob?.cancel()
            stage = UiStage.CONVERTING
            result = ConversionSummary()
            progress = ConversionProgress(total = sources.size)
            errorMessage = null
            val cancellation = AtomicBoolean(false)
            val conversionJob = scope.launch(Dispatchers.IO) {
                val completion = coroutineContext[Job]?.invokeOnCompletion { cancellation.set(true) }
                try {
                    val temporaryArchive = File.createTempFile("notes-escape-", ".zip", conversionCacheDirectory(context))
                    try {
                    val archive = FileOutputStream(temporaryArchive).use { output ->
                        val sourceSequence = sequence {
                            sources.forEach { sourceInfo ->
                                ensureActive()
                                val source = CachedSafSource(context.contentResolver, sourceInfo.uri, sourceInfo.displayName, conversionCacheDirectory(context), cancellation::get, sourceInfo.relativeDirectory)
                                try {
                                    yield(source)
                                } finally {
                                    source.close()
                                }
                            }
                        }
                        ArchiveExporter.export(sourceSequence, output, ExportOptions(format, attachments, preserve, originals, metadata)) { index, _, report ->
                            scope.launch(Dispatchers.Main.immediate) { progress = progress.withReport(report, index, sources.size) }
                        }
                    }
                    context.contentResolver.openOutputStream(destination)?.use { output ->
                        temporaryArchive.inputStream().use { it.copyTo(output) }
                    } ?: error(resources.getString(R.string.destination_open_error))
                    temporaryArchive.delete()
                    withContext(Dispatchers.Main) {
                        result = archive.summary().toUiSummary(format).copy(
                            savedFile = destination.lastPathSegment ?: resources.getString(R.string.saved_zip_default),
                            savedUri = destination
                        )
                        stage = UiStage.RESULT
                    }
                    } finally {
                        temporaryArchive.delete()
                    }
                } catch (_: CancellationException) {
                    withContext(Dispatchers.Main) {
                        result = result.copy(cancelled = true)
                        stage = UiStage.RESULT
                    }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = resources.getString(R.string.unknown_error)
                        result = progress.summary()
                        stage = UiStage.RESULT
                    }
                } finally {
                    completion?.dispose()
                    cleanConversionCache(context)
                }
            }
            activeJob = conversionJob
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        result = result.copy(cancelled = true)
        stage = UiStage.RESULT
        cleanConversionCache(context)
    }

    LaunchedEffect(Unit) {
        if (sources.isNotEmpty()) startPreflight(sources)
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.processed_locally), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.no_upload_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.home_description))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAbout = true }) { Text(stringResource(R.string.about_help)) }
                    OutlinedButton(onClick = { showPrivacy = true }) { Text(stringResource(R.string.privacy)) }
                }
            }
            item { Button(onClick = { multiple.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.select_files)) } }
            item { OutlinedButton(onClick = { folder.launch(null) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.select_folder)) } }
            item { Text(if (folderImport) stringResource(R.string.files_discovered, sources.size) else if (stage == UiStage.PREFLIGHT || sources.isNotEmpty()) pluralStringResource(R.plurals.files_selected_plural, sources.size, sources.size) else stringResource(R.string.empty_state)) }
            item { FolderStructureInfo(folderImport = folderImport, hasSources = sources.isNotEmpty()) }
            item { Text(stringResource(stage.stringRes), style = MaterialTheme.typography.titleLarge) }
            if (stage == UiStage.PREFLIGHT) {
                item { Text(stringResource(R.string.scanning_notes, progress.index, sources.size)) }
            }
            if (sources.isNotEmpty() && stage != UiStage.SELECT && stage != UiStage.CONVERTING && stage != UiStage.RESULT) {
                item { PreflightContent(preflight, format, folderImport) }
            }
            if (stage == UiStage.OPTIONS) {
                item { Text(stringResource(R.string.options_description)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = format == ExportFormat.PORTABLE_MARKDOWN, onClick = { format = ExportFormat.PORTABLE_MARKDOWN }, label = { Text(stringResource(R.string.portable_markdown)) })
                        FilterChip(selected = format == ExportFormat.OBSIDIAN_VAULT, onClick = { format = ExportFormat.OBSIDIAN_VAULT }, label = { Text(stringResource(R.string.obsidian_vault)) })
                    }
                }
                if (format == ExportFormat.OBSIDIAN_VAULT) {
                    item { Text(stringResource(R.string.obsidian_description)) }
                    item { FolderStructureInfo(folderImport, sources.isNotEmpty()) }
                    item { Option(stringResource(R.string.keep_images_attachments), attachments) { attachments = it } }
                    item { Option(stringResource(R.string.convert_handwriting_svg), preserve) { preserve = it } }
                    item { Option(stringResource(R.string.add_note_metadata), metadata) { metadata = it } }
                } else {
                    item { Option(stringResource(R.string.preserve_handwriting), preserve) { preserve = it } }
                    item { Option(stringResource(R.string.include_attachments), attachments) { attachments = it } }
                }
                item { Option(stringResource(R.string.include_originals), originals) { originals = it } }
                item { Button(enabled = sources.isNotEmpty(), onClick = { save.launch(resources.getString(if (format.isObsidian) R.string.default_obsidian_zip_filename else R.string.default_markdown_zip_filename)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_zip)) } }
            }
            if (stage == UiStage.CONVERTING) {
                item { Text(stringResource(R.string.converting_progress, progress.index, progress.total, progress.current)) }
                item { Text(stringResource(R.string.conversion_counters, progress.completed, progress.partial, progress.locked, progress.corruptFailed)) }
                item { OutlinedButton(onClick = ::cancel, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) } }
            }
            if (stage == UiStage.RESULT) {
                item {
                    ResultContent(result, format, folderImport) {
                        val output = result.savedUri
                        if (output == null) {
                            errorMessage = resources.getString(R.string.no_output_app)
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(output, "application/zip")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                errorMessage = resources.getString(R.string.no_output_app)
                            }
                        }
                    }
                }
                errorMessage?.let { item { Text(stringResource(R.string.error_message, it), color = MaterialTheme.colorScheme.error) } }
            }
            if (stage == UiStage.SELECT && sources.isEmpty()) item { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.select_empty_help)) }
        }
    }
    if (showAbout) {
        AboutHelpDialog(onDismiss = { showAbout = false })
    }
    if (showPrivacy) {
        PrivacyDialog(onDismiss = { showPrivacy = false })
    }
}

private val UiStage.stringRes: Int
    get() = when (this) {
        UiStage.SELECT -> R.string.stage_select
        UiStage.PREFLIGHT -> R.string.stage_preflight
        UiStage.OPTIONS -> R.string.stage_options
        UiStage.CONVERTING -> R.string.stage_converting
        UiStage.RESULT -> R.string.stage_result
    }

@Composable private fun FolderStructureInfo(folderImport: Boolean, hasSources: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.folder_structure_title))
        Text(stringResource(if (folderImport && hasSources) R.string.folder_structure_preserved else R.string.folder_structure_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!folderImport && hasSources) Text(stringResource(R.string.folder_structure_use_folder), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun PreflightContent(summary: PreflightSummary, format: ExportFormat, folderImport: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (format.isObsidian) stringResource(R.string.obsidian_vault) else stringResource(R.string.preflight_summary))
        if (format.isObsidian) {
            Text(stringResource(R.string.vault_notes, summary.selected))
            Text(stringResource(R.string.vault_folders, if (folderImport) summary.folderPaths.size else 0))
            Text(stringResource(R.string.vault_images_media, summary.imagesMedia))
            Text(stringResource(R.string.vault_handwriting, summary.handwritingPages))
            Text(stringResource(R.string.vault_attachments, summary.attachments))
            Text(stringResource(R.string.preflight_locked, summary.locked))
            Text(stringResource(R.string.preflight_corrupt, summary.corrupt))
            if (folderImport && summary.folderRows.isNotEmpty()) {
                summary.folderRows.toSortedMap().entries.take(8).forEach { (folder, count) -> Text(stringResource(R.string.folder_summary_row, folder, count)) }
                if (summary.folderRows.size > 8) Text(stringResource(R.string.more_folders, summary.folderRows.size - 8))
            } else if (!folderImport) Text(stringResource(R.string.selected_files_root))
        } else {
            Text(stringResource(R.string.preflight_readable, summary.readable))
            Text(stringResource(R.string.preflight_locked, summary.locked))
            Text(stringResource(R.string.preflight_corrupt, summary.corrupt))
            Text(stringResource(R.string.preflight_text, summary.text))
            Text(stringResource(R.string.preflight_handwriting, summary.handwritingPages))
            Text(stringResource(R.string.preflight_media, summary.media))
        }
    }
}

@Composable private fun ResultContent(summary: ConversionSummary, format: ExportFormat, folderImport: Boolean, onOpenOutput: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (format.isObsidian) {
            Text(stringResource(R.string.obsidian_vault_ready), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.result_vault_notes, summary.completed))
            if (folderImport) Text(stringResource(R.string.result_vault_folders, summary.foldersPreserved))
            Text(stringResource(R.string.result_vault_images, summary.imagesMedia))
            Text(stringResource(R.string.result_vault_handwriting, summary.handwritingPages))
            Text(stringResource(R.string.result_vault_attachments, summary.attachments))
            Text(stringResource(R.string.result_vault_partial, summary.partial))
            Text(stringResource(R.string.result_vault_failed, summary.failed))
            Text(stringResource(if (summary.failed > 0) R.string.vault_failed_message else if (summary.partial > 0) R.string.vault_warning_message else R.string.vault_ready_message))
        } else Text(stringResource(R.string.result_summary))
        summary.savedFile?.let { Text(stringResource(R.string.saved_zip, it), color = MaterialTheme.colorScheme.primary) }
        if (summary.savedUri != null) {
            OutlinedButton(onClick = onOpenOutput, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_output))
            }
        }
        if (!format.isObsidian) {
            Text(stringResource(R.string.result_completed, summary.completed))
            Text(stringResource(R.string.result_partial, summary.partial))
            Text(stringResource(R.string.result_locked, summary.locked))
            Text(stringResource(R.string.result_corrupt_failed, summary.corruptFailed))
        }
        if (summary.cancelled) Text(stringResource(R.string.result_cancelled), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun AboutHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var feedbackUnavailable by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_help)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.about_what_is_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_what_is_body))
                Text(stringResource(R.string.about_export_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_export_steps))
                Text(stringResource(R.string.about_preserves_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_preserves_body))
                Text(stringResource(R.string.about_limitations_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_limitations_body))
                Text(stringResource(R.string.independent_disclaimer), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    val intent = feedbackIntent(context)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        feedbackUnavailable = false
                    } else {
                        feedbackUnavailable = true
                    }
                }) { Text(stringResource(R.string.send_feedback)) }
                if (feedbackUnavailable) {
                    Text(stringResource(R.string.no_email_app), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } }
    )
}

private fun feedbackIntent(context: Context): Intent {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val version = packageInfo.versionName ?: "?"
    val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    return Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${context.getString(R.string.support_email)}")
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.feedback_body, version, androidVersion, device))
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_title)) },
        text = { Text(stringResource(R.string.privacy_body), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } }
    )
}

@Composable private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked = checked, onCheckedChange = onChange) }
}

private fun PreflightSummary.add(result: ParseResult, relativeDirectory: List<String>, folderImport: Boolean): PreflightSummary {
    val isCorrupt = result.status == ParseStatus.CORRUPT || result.status == ParseStatus.FAILED
    val hasText = result.topLevelText?.isNotBlank() == true || result.pages.any { page -> page.elements.any { it is RichTextElement && it.text.isNotBlank() } }
    val handwritingPages = result.pages.count { page -> page.elements.any { it is HandwritingElement && it.strokes.isNotEmpty() } }
    val hasMedia = result.media.isNotEmpty() || result.pages.any { page -> page.elements.any { it is ImageElement } }
    val imageCount = result.pages.sumOf { page -> page.elements.count { it is ImageElement } } + result.media.count { it.filename.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") }
    val attachmentCount = result.pages.sumOf { page -> page.elements.count { it is com.notesescape.sdocx.core.AttachmentElement } } + result.media.count { it.filename.substringAfterLast('.').lowercase() !in setOf("jpg", "jpeg", "png", "webp") }
    val paths = if (folderImport) relativeDirectory.runningFold(emptyList<String>()) { acc, segment -> acc + segment }.drop(1).map { it.joinToString("/") }.toSet() else emptySet()
    val rows = if (folderImport && relativeDirectory.isNotEmpty()) folderRows(relativeDirectory) else emptyMap()
    return copy(
        readable = readable + if (!isCorrupt) 1 else 0,
        locked = locked + if (result.status == ParseStatus.LOCKED) 1 else 0,
        corrupt = corrupt + if (isCorrupt) 1 else 0,
        text = text + if (hasText) 1 else 0,
        handwriting = handwriting + if (handwritingPages > 0) 1 else 0,
        media = media + if (hasMedia) 1 else 0,
        imagesMedia = imagesMedia + imageCount + attachmentCount,
        handwritingPages = this.handwritingPages + handwritingPages,
        attachments = attachments + attachmentCount,
        folderPaths = folderPaths + paths,
        folderRows = (folderRows.keys + rows.keys).associateWith { key -> (folderRows[key] ?: 0) + (rows[key] ?: 0) }
    )
}

private fun folderRows(relativeDirectory: List<String>): Map<String, Int> = mapOf(relativeDirectory.first() to 1)

private fun ConversionProgress.withReport(report: NoteReport, index: Int, total: Int): ConversionProgress {
    val completed = completed + if (report.status == ParseStatus.SUCCESS) 1 else 0
    val partial = partial + if (report.status == ParseStatus.PARTIAL) 1 else 0
    val locked = locked + if (report.status == ParseStatus.LOCKED) 1 else 0
    val failed = corruptFailed + if (report.status == ParseStatus.CORRUPT || report.status == ParseStatus.FAILED) 1 else 0
    return copy(current = report.sourceFilename, index = index, total = total, completed = completed, partial = partial, locked = locked, corruptFailed = failed)
}

private fun ConversionProgress.summary() = ConversionSummary(completed = completed, partial = partial, locked = locked, corruptFailed = corruptFailed, failed = corruptFailed)

private fun ArchiveSummary.toUiSummary(preset: ExportFormat) = ConversionSummary(
    completed = notesConverted - partial,
    partial = partial,
    corruptFailed = failed,
    preset = preset,
    foldersPreserved = foldersPreserved,
    imagesMedia = imagesMediaPreserved,
    handwritingPages = handwritingPagesPreserved,
    attachments = attachmentsPreserved,
    failed = failed
)

private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "note.sdocx"

private fun incomingSources(intent: Intent): List<SafSource> {
    val shared: List<Uri> = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
    val single = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    }
    val uris = if (shared.isNotEmpty()) shared else listOfNotNull(intent.data, single)
    return uris.filter { shared.isNotEmpty() || it.toString().contains(".sdocx", ignoreCase = true) }.distinctBy(Uri::toString).sortedBy(Uri::toString).map { SafSource(it, displayName(it)) }
}
