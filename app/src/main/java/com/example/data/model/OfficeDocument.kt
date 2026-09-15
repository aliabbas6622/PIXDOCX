package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DocumentType {
    DOC, XLS, PPT, PDF
}

@Entity(tableName = "office_documents")
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
    val sizeLabel: String = "4 KB"
)
