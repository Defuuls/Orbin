package com.orbin.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.orbin.core.model.BoardId
import com.orbin.core.model.SavedSearch
import com.orbin.core.model.SearchContentType
import com.orbin.core.model.SearchFilters

@Entity("saved_searches")
data class SavedSearchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val board: String? = null,
    val mediaOnly: Boolean = false,
    val minReplies: Int? = null,
    val includeNsfw: Boolean = true,
    val contentTypes: String = "", // Comma-separated enum names
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    fun toDomainModel(): SavedSearch =
        SavedSearch(
            id = id,
            text = text,
            board = board?.let { BoardId(it) },
            filters =
                SearchFilters(
                    mediaOnly = mediaOnly,
                    minReplies = minReplies,
                    includeNsfw = includeNsfw,
                    contentTypes =
                        if (contentTypes.isEmpty()) {
                            emptySet()
                        } else {
                            contentTypes
                                .split(",")
                                .mapNotNull { name ->
                                    SearchContentType.entries.find { it.name == name }
                                }.toSet()
                        },
                ),
            createdAtMillis = createdAtMillis,
        )
}

fun SavedSearch.toEntity(): SavedSearchEntity =
    SavedSearchEntity(
        id = id,
        text = text,
        board = board?.value,
        mediaOnly = filters.mediaOnly,
        minReplies = filters.minReplies,
        includeNsfw = filters.includeNsfw,
        contentTypes = filters.contentTypes.joinToString(",") { it.name },
        createdAtMillis = createdAtMillis,
    )
