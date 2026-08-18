# Obsidian Vault export

Notes Escape creates one ZIP that can be extracted and opened as an Obsidian vault. In **Select folder** mode, the filesystem hierarchy below the selected SAF folder is preserved:

```text
Notes/Work/Meeting.md
Attachments/Work/Meeting/image-001.jpg
_Notes Escape/migration-report.md
_Notes Escape/migration-report.json
```

The vault uses deterministic vault-root-relative wikilinks such as `![[Attachments/Work/Meeting/image-001.jpg]]` and `[[Attachments/Work/Meeting/document.pdf]]`. Portable Markdown remains standard Markdown and uses ordinary relative links.

Folder selection is filesystem provenance only. The app does not claim to recover Samsung Notes internal notebooks or folders unless that hierarchy is independently validated in SDOCX metadata. Individually selected files are treated as root-level notes because Android does not reliably expose their original common folder.

Obsidian notes optionally include safe YAML front matter with the parsed title and, only when present in SDOCX metadata, `created` and `modified`, plus `source`, `source_format`, and `migration_status`. The **Add note metadata** option controls this front matter. Names are sanitized, and collisions receive `(2)`, `(3)`, and so on within the same output folder.

Folder preservation is automatic for **Select folder** and is informational in the UI; there is no toggle that can pretend to disable it. Individual file selection is flattened at the vault root. Preflight and result counts use parsed note reports: a preserved folder is one unique directory under `Notes/` represented by an exported note.

Known limitations include unsupported proprietary objects, locked notes, corrupt exports, imperfect rich-text/page-layout fidelity, and media types that Samsung Notes or the parser cannot identify. Direct export into an existing vault is not supported; the ZIP is one atomic migration output and temporary ZIP data is removed on success, cancellation, and failure.
