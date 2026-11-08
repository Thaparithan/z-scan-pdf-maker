package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val fileName: String,
    val filePath: String,
    val thumbnailPath: String?,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val pageSizeLabel: String = "A4",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class DocumentSortOption(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)")
}

enum class DocumentLayoutMode {
    GRID,
    LIST
}
