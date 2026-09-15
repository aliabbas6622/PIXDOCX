# PixDocx — TODO

Last updated: 2026-09-15

## Legend
- [ ] = open · [x] = done · Priorities: 🔴 P0 (broken/ugly) · 🟡 P1 (important) · 🟢 P2 (nice-to-have)

---

## 1. File viewer — make viewing actually good (P0, **done 2026-09-15, on-device test pending**)

Current state: every file opens in one of 3 "editor" screens. A .md/.txt/.pdf file opens as a
raw plain-text field (markdown shown as literal `#`/`**` syntax). DOCX imports lose all
formatting. PDF is best-effort text. There is no proper read-only viewer.

### 1.1 Markdown viewer (P0) ✅
- [x] `MarkdownText.kt` — custom dependency-free renderer: headings, **bold**,
      *italic*, ~~strike~~, `code`, fenced code blocks, bullet/ordered lists,
      task checkboxes, blockquotes, pipe tables, horizontal rules
- [x] Edit toggle: docs open in read-only view; pencil icon switches to editor

### 1.2 PDF viewer (P0) ✅
- [x] `PdfViewerScreen.kt` — real page rendering via `android.graphics.pdf.PdfRenderer`
      (no dependency), 2x raster, vertical scroll, page numbers, white page cards
- [x] Original file preserved in `files/imports/` at import time (`localFilePath` DB column)
- [x] Graceful fallback screen when original file unavailable (shows extracted notes)

### 1.3 DOCX viewer fidelity (P1) ✅ (basic)
- [x] DOCX import now emits markdown: bold/italic/underline runs, Heading1-9 styles,
      Title style, list numbering (numPr → "- " bullets)
- [ ] Tables in DOCX (still flattened) — future work

### 1.4 Text/code viewer (P1) ✅
- [x] Code-like files (by extension list + heuristic) render monospace,
      horizontally scrollable

### 1.5 Viewer routing (P0) ✅
- [x] MainActivity routes by type: PDF → PdfViewerScreen, DOC → DocViewerScreen
      (read-only) with Edit toggle → WordEditorScreen, XLS → grid, PPT → slides
- [x] Room migration 2→3 adds `localFilePath` column (non-destructive)

## 2. File type converters / export (P1, **done 2026-09-15, on-device share test pending**)

### 2.0 Import fixes — "not loading all files from the device" (P0, done 2026-09-15)
- [x] **PDF FlateDecode**: real-world PDFs store content streams zlib-compressed;
      FileImporter now inflates every `stream...endstream` block (zlib) and re-scans it
      for Tj/TJ text. Uncompressed streams still handled; scanned-image PDFs fall back
      to the notice + native PdfViewer (original always preserved)
- [x] **Multi-select import**: picker switched to OpenMultipleDocuments; batch imports
      run in parallel with a live "Importing N files..." progress row on Home
- [x] **Duplicate skip**: files with same title + size are not imported twice
      (new DAO query countByTitleAndSize)
- [x] **Zip cap raised**: 8 MB → 24 MB per OOXML entry (big DOCX/XLSX no longer dropped)
- [x] `FileImporterPdfTest` — 4 Robolectric tests incl. a generated Flate-compressed PDF
- [ ] On device: pick several PDFs/DOCX/XLSX from Download and confirm all open;
      note that scanned (image-only) PDFs intentionally show the fallback notice
- [x] `FileConverter.kt` — dependency-free converters from the internal DOC/XLS/PPT/PDF model:
      Markdown (pipe tables for sheets, numbered sections for slides), plain text,
      CSV (formula-evaluated, quoted), styled HTML, and real paginated PDF via
      `android.graphics.pdf.PdfDocument` (A4, title + body paints, footer, page numbers)
- [x] `ExportDocumentDialog` upgraded: format chips per document type, live conversion
      preview, "Copy Text" copies the converted output, Share writes
      `files/exports/<ts>_<title>.<ext>` and sends it via `ACTION_SEND` + FileProvider
- [x] FileProvider registered in the manifest (`@xml/file_paths`: exports/ + imports/)
- [x] `FileConverterTest` — 12 Robolectric tests (md/txt/csv/html round trips, PDF line
      layout, file write). Byte-level PDF output verified on-device, since Robolectric's
      PdfDocument shadow cannot run the native PDF writer on the JVM

## 3. Open-with integration (P1)
- [ ] Add intent-filter so "Open with PixDocx" appears in system file manager
      (md, txt, csv, pdf, docx, xlsx, pptx mime types)
- [ ] Handle ACTION_VIEW in MainActivity: import the uri then open the viewer
- [ ] Unit tests for FileImporter (md/csv/docx/xlsx/pptx fixtures) — started earlier, not committed

## 4. UI polish backlog (from user feedback round 1)
- [x] Rebrand header to "PixDocx" (commit 152596a)
- [x] Header rounded bottom corners (20dp)
- [x] Reduce excessive bold text app-wide (Type.kt retuned)
- [x] Text overflow protection (`maxLines`/`ellipsis`) on cards, cells, slide titles
- [x] Faster animations (300ms → ~120ms)

## 5. Testing on device (Pixel 7a)
- [x] Build compiles, APK installs, app launches with no fatal exceptions
- [ ] Import test files pushed to /sdcard/Download: sample.pdf, notes.md, inventory.csv
      (blocked: phone was locked during test session — resume when unlocked)
- [ ] Confirm no regression: formulas still evaluate, auto-save, migrations 1→2→3 intact

## 6. Ideas / later (P2)
- [ ] Search inside PDF viewer
- [ ] Dark/light theme toggle in header
- [x] Export to PDF (PdfDocument API) — done in `FileConverter`
- [ ] Recent files section on home

## 7. Repo & app hygiene — "would a professional ship this?" (done 2026-09-15)
- [x] **No machine-specific paths in VCS**: removed the hard-coded
      `org.gradle.java.home=C:/Program Files/Android/...` from `gradle.properties`
      (it made the project unbuildable on any other machine/CI); documented the
      user-level override in `README.md`
- [x] **Committed Gradle wrapper scripts** (`gradlew`, `gradlew.bat`) — previously only
      the wrapper jar was committed, so the project could not be built from a terminal
- [x] **Dead dependencies pruned**: Firebase (AI/AppCheck/BOM), Retrofit, Moshi, OkHttp,
      logging-interceptor and the secrets/google-services plugins were all shipped but
      never referenced (the manifest has no INTERNET permission). Debug APK 23.1 MB → 17.2 MB
- [x] **Template leftovers removed**: `Greeting()` + its screenshot test/baseline,
      `ExampleUnitTest` (2+2), `Example*` test class names; placeholder theme
      `Theme.MyApplication`/`MyApplicationTheme` → `Theme.PixDocx`/`PixDocxTheme`
- [x] **Imports no longer fail silently**: duplicates returned early with no feedback and
      exceptions escaped the view-model coroutine. Every picked file now yields an
      `ImportOutcome`, budgets into one snackbar sentence (`ImportSummaryTest`), and batch
      progress lives in the view model so it survives rotation
- [x] `README.md` covering features, architecture, build (incl. the JDK 17–25 requirement)
      and the test map; `metadata.json` no longer claims an unused Gemini capability
- [x] **Release builds no longer require the private keystore**: `:app:assembleRelease`
      hard-failed on a missing `my-upload-key.jks`; it now assembles an unsigned APK when no
      keystore is configured (R8 + resource shrinking still run — release APK 1.78 MB)
- [x] Fixed the library card meta line rendering `PDF • PDF • Sep 15` (found by dumping the
      UI hierarchy on-device; PDFs have no editable metric, so they show type + date)
- [x] Compiler warnings cleared (AutoMirrored icons, Room downgrade API)
- [x] On-device smoke test (Pixel 7a): app installs over existing data, launches in ~1 s, 11
      existing documents render, no exceptions in logcat
- [ ] **Package rename**: still `namespace = "com.example"` with
      `applicationId = "com.aistudio.wpsoffice.oxfld"` — cannot ship as-is; needs a
      deliberate decision because the applicationId change resets installed apps
- [ ] **Localisation**: ~150 UI strings are hard-coded in Kotlin; `strings.xml` has only
      `app_name`. Extract to resources for translation + typography/a11y review
