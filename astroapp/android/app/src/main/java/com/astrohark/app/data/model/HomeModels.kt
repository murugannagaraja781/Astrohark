package com.astrohark.app.data.model

import com.google.gson.annotations.SerializedName

data class HomeDataResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("data") val data: HomeData
)

data class HomeData(
    @SerializedName("banners") val banners: List<Banner>,
    @SerializedName("rituals") val rituals: List<Ritual>,
    @SerializedName("homeConfig") val homeConfig: HomeConfig? = null
)

data class HomeConfig(
    @SerializedName("grid_services") val gridServices: List<GridService>? = null
)

data class GridService(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("title_tamil") val titleTamil: String? = null,
    @SerializedName("icon") val icon: String,
    @SerializedName("route") val route: String
)
