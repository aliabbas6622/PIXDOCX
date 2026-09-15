package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DocumentType {
    DOC, XLS, PPT, PDF
}

/**
 * Performance notes (see OPTIMIZE_PERFORMANCE.md / DEEP_OPTIMIZATION_ANDROID17_PIXEL7A.md):
 * - @Immutable lets the Compose compiler treat instances as stable, skipping
 *   recompositions of DocumentCard items whose data has not changed.
 * - Indices cover the hot query paths: tab filtering (type), pinned sorting,
 *   recency ordering (lastModified), category filtering, and title prefix search.
 */
@Immutable
@Entity(
    tableName = "office_documents",
    indices = [
        Index(value = ["type"], name = "idx_type"),
        Index(value = ["isPinned"], name = "idx_pinned"),
        Index(value = ["lastModified"], name = "idx_modified"),
        Index(value = ["category"], name = "idx_category"),
        Index(value = ["type", "isPinned"], name = "idx_type_pinned"),
        Index(value = ["title"], name = "idx_title")
    ]
)
data class OfficeDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: DocumentType,
    val content: String,
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val category: String = "All",
    val wordCount: Int = 0,
    val sheetRows: Int = 0,
    val slideCount: Int = 0,
    val sizeLabel: String = "4 KB",
    /** Path to the preserved original file (PDFs need it for real rendering). */
    val localFilePath: String = ""
)
