package com.astrohark.app.data.model

import com.google.gson.annotations.SerializedName

data class Ritual(
    @SerializedName("_id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("title_ta") val titleTamil: String? = null,
    @SerializedName("subtitle") val subtitle: String,
    @SerializedName("subtitle_ta") val subtitleTamil: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("description_ta") val descriptionTamil: String? = null,
    @SerializedName("imageUrl") val imageUrl: String,
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("isActive") val isActive: Boolean = true,
    @SerializedName("order") val order: Int = 0
)

data class RitualResponse(
    val ok: Boolean,
    val data: List<Ritual>
)
