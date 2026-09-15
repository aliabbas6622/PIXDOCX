package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors
import com.example.data.model.DocumentType
import com.example.data.model.OfficeDocument
import com.example.data.model.SlideDeck
import com.example.data.model.SlideItem
import com.example.data.model.SlideLayout
import com.example.data.model.SpreadsheetGrid
import androidx.room.RoomDatabase.JournalMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [OfficeDocument::class], version = 3, exportSchema = false)
abstract class OfficeDatabase : RoomDatabase() {
    abstract fun officeDocumentDao(): OfficeDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: OfficeDatabase? = null

        /**
         * Migration 1 -> 2: add query indices for hot paths (type filter,
         * pinned sort, recency order, category filter, title search).
         * Creating them here avoids a destructive rebuild for existing users.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_type ON office_documents (type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pinned ON office_documents (isPinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_modified ON office_documents (lastModified)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_category ON office_documents (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_type_pinned ON office_documents (type, isPinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_title ON office_documents (title)")
            }
        }

        /**
         * Migration 2 -> 3: store the path of the preserved original file so
         * PDFs can be re-rendered with PdfRenderer at open time.
         */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE office_documents ADD COLUMN localFilePath TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): OfficeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfficeDatabase::class.java,
                    "wps_office_database.db"
                )
                // WAL allows readers to proceed concurrently with a writer,
                // keeping list queries responsive while an auto-save commits.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                // Dedicated executors: 2 threads for queries, a single
                // serialized thread for transactions (write ordering).
                .setQueryExecutor(Executors.newFixedThreadPool(2))
                .setTransactionExecutor(Executors.newSingleThreadExecutor())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // Safety net for version downgrades (e.g. sideloading an older
                // APK): recreate the DB instead of crashing. Upgrades keep the
                // proper MIGRATION_1_2 path and preserve user data.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .addCallback(OfficeDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class OfficeDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialDocuments(database.officeDocumentDao())
                    }
                }
            }
        }

        suspend fun populateInitialDocuments(dao: OfficeDocumentDao) {
            if (dao.getDocumentCount() > 0) return

            // 1. DOC: Quarterly Business Review
            val docText = """
# Quarterly Business Review & Strategic Roadmap
*Author: Strategy & Operations Team • Q3 Performance*

---

## Executive Summary
During the past quarter, our mobile product suite achieved exceptional milestones across user retention, performance benchmarks, and feature delivery. Native Android architectural improvements delivered a **45% reduction in latency** and seamless offline reliability.

### Key Performance Highlights:
• **99.98% Crash-Free Rate** across all active devices
• **4.8/5.0 User Rating** on productivity workflows
• **2.4x Speedup** in spreadsheet formula evaluation
• Full offline document editing without network dependency

---

## Strategic Action Items:
[x] Finalize native spreadsheet formula engine (SUM, AVERAGE, MIN, MAX)
[x] Implement fluid slide deck presentation mode with gesture transitions
[ ] Expand export capabilities to PDF, Markdown, and CSV
[ ] Ship enterprise document encryption and local backup
            """.trimIndent()

            val seedDocs = mutableListOf<OfficeDocument>()

            seedDocs.add(
                OfficeDocument(
                    title = "Quarterly Business Review",
                    type = DocumentType.DOC,
                    content = docText,
                    isPinned = true,
                    category = "Work",
                    wordCount = 145,
                    sizeLabel = "16 KB"
                )
            )

            // 2. XLS: Financial Budget Model
            val grid = SpreadsheetGrid()
            // Headers
            grid.setCell("A1", "Category")
            grid.setCell("B1", "Q1")
            grid.setCell("C1", "Q2")
            grid.setCell("D1", "Total")

            // Rows
            grid.setCell("A2", "Gross Revenue")
            grid.setCell("B2", "125000")
            grid.setCell("C2", "168000")
            grid.setCell("D2", "=B2+C2")

            grid.setCell("A3", "R&D Software")
            grid.setCell("B3", "32000")
            grid.setCell("C3", "34000")
            grid.setCell("D3", "=B3+C3")

            grid.setCell("A4", "Marketing & Growth")
            grid.setCell("B4", "18000")
            grid.setCell("C4", "22000")
            grid.setCell("D4", "=B4+C4")

            grid.setCell("A5", "Operations")
            grid.setCell("B5", "14000")
            grid.setCell("C5", "15000")
            grid.setCell("D5", "=B5+C5")

            grid.setCell("A6", "Total Expenses")
            grid.setCell("B6", "=SUM(B3:B5)")
            grid.setCell("C6", "=SUM(C3:C5)")
            grid.setCell("D6", "=SUM(D3:D5)")

            grid.setCell("A7", "Net Operating Profit")
            grid.setCell("B7", "=B2-B6")
            grid.setCell("C7", "=C2-C6")
            grid.setCell("D7", "=D2-D6")

            seedDocs.add(
                OfficeDocument(
                    title = "Annual Department Budget",
                    type = DocumentType.XLS,
                    content = grid.serialize(),
                    isPinned = true,
                    category = "Finance",
                    sheetRows = 7,
                    sizeLabel = "24 KB"
                )
            )

            // 3. PPT: Mobile Innovation Deck
            val deck = SlideDeck(
                themeColorHex = "#0F172A",
                accentColorHex = "#EA580C",
                slides = mutableListOf(
                    SlideItem(
                        layout = SlideLayout.TITLE_SLIDE,
                        title = "WPS Office Android",
                        subtitle = "High Performance Native Document Suite\nPresenter: Mobile Architecture Group",
                        notes = "Welcome everyone. Today we showcase the high performance native engine."
                    ),
                    SlideItem(
                        layout = SlideLayout.BIG_STAT,
                        title = "Performance Breakthrough",
                        statValue = "60 FPS",
                        statLabel = "Consistent frame rate during large document scrolling and formula calculation",
                        notes = "Emphasize native Jetpack Compose rendering and zero-lag editing."
                    ),
                    SlideItem(
                        layout = SlideLayout.TITLE_AND_CONTENT,
                        title = "Core Capabilities",
                        content = "• Word Processor: Rich text, formatting, search & replace, word metrics\n• Calc Spreadsheet: Real-time formulas, sticky headers, CSV export\n• Slide Deck: Multi-slide carousel, layout templates, presentation mode\n• 100% Offline: Local Room database, auto-save, instant startup",
                        notes = "Review all three core modules."
                    ),
                    SlideItem(
                        layout = SlideLayout.TWO_COLUMN,
                        title = "Traditional Web Apps vs Native Android",
                        content = "• High memory overhead\n• Sluggish touch response\n• Unreliable offline caching\n• Large bundle sizes",
                        secondaryContent = "• Zero lag native Composables\n• Native SQLite Room queries\n• Hardware accelerated rendering\n• Instant resume & auto-save",
                        notes = "Highlight our architectural advantages."
                    )
                )
            )

            seedDocs.add(
                OfficeDocument(
                    title = "Mobile Innovation Keynote",
                    type = DocumentType.PPT,
                    content = deck.serialize(),
                    isPinned = false,
                    category = "Pitch",
                    slideCount = 4,
                    sizeLabel = "38 KB"
                )
            )

            // 4. DOC: Project Proposal
            val proposal = """
# Product Proposal: Seamless Cloud-Free Office
*Confidential • For Internal Review*

### 1. Objective
Deliver a responsive, distraction-free office workspace for students, executives, and developers who require reliable offline editing on Android phones and tablets.

### 2. Design Principles
1. **Speed First:** Immediate startup with zero splash delays.
2. **Data Privacy:** All documents are stored securely on-device in local storage.
3. **Ergonomic Controls:** Thumb-friendly formatting toolbars placed at the bottom for comfortable one-handed typing.
            """.trimIndent()

            seedDocs.add(
                OfficeDocument(
                    title = "Offline Office Whitepaper",
                    type = DocumentType.DOC,
                    content = proposal,
                    isPinned = false,
                    category = "Research",
                    wordCount = 76,
                    sizeLabel = "10 KB"
                )
            )

            // Single-transaction batch insert instead of 4 individual commits
            dao.bulkInsertDocuments(seedDocs)
        }
    }
}
