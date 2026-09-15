package com.example.data.repository

import com.example.data.db.OfficeDocumentDao
import com.example.data.model.DocumentType
import com.example.data.model.OfficeDocument
import kotlinx.coroutines.flow.Flow

class OfficeRepository(private val dao: OfficeDocumentDao) {

    val allDocuments: Flow<List<OfficeDocument>> = dao.getAllDocuments()

    val pinnedDocuments: Flow<List<OfficeDocument>> = dao.getPinnedDocuments()

    fun getDocumentsByType(type: DocumentType): Flow<List<OfficeDocument>> =
        dao.getDocumentsByType(type)

    fun searchDocuments(query: String): Flow<List<OfficeDocument>> =
        dao.searchDocuments(query)

    fun getDocumentById(id: Long): Flow<OfficeDocument?> =
        dao.getDocumentById(id)

    suspend fun getDocumentByIdDirect(id: Long): OfficeDocument? =
        dao.getDocumentByIdDirect(id)

    suspend fun countByTitleAndSize(title: String, sizeLabel: String): Int =
        dao.countByTitleAndSize(title, sizeLabel)

    suspend fun insert(document: OfficeDocument): Long =
        dao.insertDocument(document)

    suspend fun update(document: OfficeDocument) =
        dao.updateDocument(document)

    suspend fun delete(document: OfficeDocument) =
        dao.deleteDocument(document)

    suspend fun deleteById(id: Long) =
        dao.deleteDocumentById(id)

    suspend fun setPinned(id: Long, isPinned: Boolean) =
        dao.setPinned(id, isPinned)

    suspend fun rename(id: Long, newTitle: String) =
        dao.renameDocument(id, newTitle)

    suspend fun duplicateDocument(id: Long): Long {
        val original = dao.getDocumentByIdDirect(id) ?: return -1
        val copy = original.copy(
            id = 0,
            title = "${original.title} (Copy)",
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            isPinned = false
        )
        return dao.insertDocument(copy)
    }
}
