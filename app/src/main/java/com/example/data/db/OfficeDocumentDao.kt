package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.DocumentType
import com.example.data.model.OfficeDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficeDocumentDao {
    @Query("SELECT * FROM office_documents ORDER BY isPinned DESC, lastModified DESC")
    fun getAllDocuments(): Flow<List<OfficeDocument>>

    @Query("SELECT * FROM office_documents WHERE type = :type ORDER BY isPinned DESC, lastModified DESC")
    fun getDocumentsByType(type: DocumentType): Flow<List<OfficeDocument>>

    @Query("SELECT * FROM office_documents WHERE isPinned = 1 ORDER BY lastModified DESC")
    fun getPinnedDocuments(): Flow<List<OfficeDocument>>

    @Query("SELECT * FROM office_documents WHERE id = :id LIMIT 1")
    fun getDocumentById(id: Long): Flow<OfficeDocument?>

    @Query("SELECT * FROM office_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdDirect(id: Long): OfficeDocument?

    @Query("SELECT * FROM office_documents WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY lastModified DESC")
    fun searchDocuments(query: String): Flow<List<OfficeDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: OfficeDocument): Long

    /**
     * Batch insert inside a single transaction. A transaction amortizes the
     * WAL/journal commit cost across all rows, which is orders of magnitude
     * faster than N individual inserts for seed data or bulk imports.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<OfficeDocument>)

    /**
     * Convenience wrapper kept suspend + @Transaction so callers get
     * atomic, single-commit semantics for bulk operations.
     */
    @Transaction
    suspend fun bulkInsertDocuments(documents: List<OfficeDocument>) {
        insertDocuments(documents)
    }

    @Update
    suspend fun updateDocument(document: OfficeDocument)

    @Delete
    suspend fun deleteDocument(document: OfficeDocument)

    @Query("DELETE FROM office_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("UPDATE office_documents SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Long, isPinned: Boolean)

    @Query("UPDATE office_documents SET title = :newTitle, lastModified = :time WHERE id = :id")
    suspend fun renameDocument(id: Long, newTitle: String, time: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM office_documents")
    suspend fun getDocumentCount(): Int
}
