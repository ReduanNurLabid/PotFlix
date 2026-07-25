package com.potflix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.potflix.domain.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val type: String,
    val icon: String
) {
    fun toDomainModel(): Category {
        return Category(
            id = id,
            name = name,
            url = url,
            type = type,
            icon = icon
        )
    }
}
