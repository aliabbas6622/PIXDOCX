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

## 2. Open-with integration (P1)
- [ ] Add intent-filter so "Open with PixDocx" appears in system file manager
      (md, txt, csv, pdf, docx, xlsx, pptx mime types)
- [ ] Handle ACTION_VIEW in MainActivity: import the uri then open the viewer
- [ ] Unit tests for FileImporter (md/csv/docx/xlsx/pptx fixtures) — started earlier, not committed

## 3. UI polish backlog (from user feedback round 1)
- [x] Rebrand header to "PixDocx" (commit 152596a)
- [x] Header rounded bottom corners (20dp)
- [x] Reduce excessive bold text app-wide (Type.kt retuned)
- [x] Text overflow protection (`maxLines`/`ellipsis`) on cards, cells, slide titles
- [x] Faster animations (300ms → ~120ms)

## 4. Testing on device (Pixel 7a)
- [x] Build compiles, APK installs, app launches with no fatal exceptions
- [ ] Import test files pushed to /sdcard/Download: sample.pdf, notes.md, inventory.csv
      (blocked: phone was locked during test session — resume when unlocked)
- [ ] Confirm no regression: formulas still evaluate, auto-save, migrations 1→2→3 intact

## 5. Ideas / later (P2)
- [ ] Search inside PDF viewer
- [ ] Dark/light theme toggle in header
- [ ] Export to PDF (PdfDocument API)
- [ ] Recent files section on home
