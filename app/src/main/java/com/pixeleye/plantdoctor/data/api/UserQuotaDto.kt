package com.pixeleye.plantdoctor.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserQuotaDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("daily_count")
    val dailyCount: Int = 0,
    @SerialName("last_scan_date")
    val lastScanDate: String = "",
    @SerialName("is_premium")
    val isPremium: Boolean = false
)
