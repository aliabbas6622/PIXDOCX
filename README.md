# PixDocx

A fully offline office suite for Android: documents, spreadsheets, presentations and
PDF viewing, built with Kotlin, Jetpack Compose and Room. No account, no backend,
no network permission — every file lives in app-private storage on the device.

## What it does

| Area | Status |
| --- | --- |
| **Docs** | Markdown-backed editor + read-only viewer with a real markdown renderer (headings, lists, tables, code blocks, quotes) |
| **Sheets** | Grid editor with a formula engine, CSV import/export |
| **Slides** | Deck editor with layouts, presenter mode and speaker notes |
| **PDF** | Real page rendering via the platform `PdfRenderer`, lazy per-page rasterisation, pinch-to-zoom, double-tap reset |
| **Import** | `.md`, `.txt`, `.csv`, `.tsv`, `.docx`, `.xlsx`, `.pptx`, `.pdf` — multi-select, original bytes preserved |
| **Export** | Markdown, plain text, CSV, styled HTML, paginated PDF; share via `ACTION_SEND` + `FileProvider` |

Everything is dependency-free where the platform allows it: the markdown renderer,
the OOXML parsers, the converters and the PDF writer are all hand-written on top of
Android/first-party APIs.

## Architecture

```
app/src/main/java/com/example/
├── MainActivity.kt              Compose host; routes to a viewer/editor per document type
├── data/
│   ├── model/                   OfficeDocument (Room entity), spreadsheet + slide models
│   ├── db/                      Room database, DAO, versioned migrations
│   └── repository/              Single write path for documents
├── ui/
│   ├── OfficeViewModel.kt       All screen state: search, sort, open document, undo/redo, autosave, import
│   ├── ImportOutcome.kt         Per-file import result + batch summary wording (pure, unit-tested)
│   ├── home/                    Library list, search, sort, new-document sheet
│   ├── viewer/                  Read-only Doc and PDF viewers
│   ├── writer/, calc/, slides/  The three editors
│   ├── common/                  Shared components: cards, dialogs, markdown renderer
│   └── theme/                   Colour, type and the app theme
└── util/
    ├── FileImporter.kt          Device file → internal model (OOXML, CSV, PDF text)
    ├── FileConverter.kt         Internal model → md / txt / csv / html / pdf
    └── PerformanceMetrics.kt    In-process timing of the hot paths
```

Design rules the code follows:

- **One source of truth per screen.** Screen state lives in `OfficeViewModel`; composables
  hold only transient UI state (which dialog is open, whether search is expanded).
- **Heavy work off the main thread.** Parsing, conversion and PDF rasterisation run on
  `Dispatchers.IO`/`Default`; the UI gets state flows.
- **Nothing fails silently.** Imports report per-file outcomes and are summarised into one
  sentence; PDFs that cannot be converted to text say so instead of showing mojibake.
- **No new dependencies without a reason.** The APK ships the platform and Compose, nothing else.

## Building

Requires the Android SDK (`local.properties` → `sdk.dir`) and a JDK in the 17–25 range
(AGP does not support newer JDKs yet).

```bash
./gradlew :app:assembleDebug      # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # JVM + Robolectric unit tests
./gradlew :app:connectedAndroidTest
```

If your default `java` is too new, point Gradle at a supported JDK from your **user**
properties file (`%USERPROFILE%\.gradle\gradle.properties` on Windows,
`~/.gradle/gradle.properties` elsewhere) rather than committing a path to this repo:

```properties
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

`gradle.properties` in the repo deliberately contains no machine-specific paths.

## Testing

Unit tests run on the JVM; anything touching Android APIs uses Robolectric.

| Test | Covers |
| --- | --- |
| `FileConverterTest` | Markdown/text/CSV/HTML/PDF conversion round trips |
| `FileImporterPdfTest` | Text extraction from real (Flate-compressed) PDF bytes, CSV/Markdown imports, original-file preservation |
| `PdfTextQualityTest` | Mojibake detection for PDFs with embedded font encodings |
| `ImportSummaryTest` | Import feedback wording, singular/plural, mixed batches |
| `WordCountTest` | Word counting used by the card badges |
| `OfficeFormulaEngineTest` | Spreadsheet formula evaluation |

Robolectric's `PdfDocument` shadow cannot run the native PDF writer, so byte-level PDF
output is verified on device.

## Known limits

- Scanned (image-only) PDFs have no text layer: the original is preserved and rendered in
  the viewer, but text export is not possible.
- DOCX tables and complex OOXML layout are flattened to markdown; PDFs are rendered
  faithfully but are not editable.
- Legacy binary formats (`.doc`, `.xls`, `.ppt`) are read as best-effort text.

See `TODO.md` for the live backlog.
