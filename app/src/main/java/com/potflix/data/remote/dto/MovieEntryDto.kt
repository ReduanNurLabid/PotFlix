package com.potflix.data.remote.dto

data class MovieEntryDto(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val title: String? = null,
    val year: Int? = null,
    val quality: String? = null,
    val type: String? = null,
    val isDualAudio: Boolean? = null,
    val rawMeta: String? = null,
    val extension: String? = null,
    val isVideo: Boolean? = null,
    val isImage: Boolean? = null,
    val categoryId: String? = null,
    val categoryName: String? = null
)
