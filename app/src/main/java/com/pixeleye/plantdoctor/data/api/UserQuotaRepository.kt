package com.pixeleye.plantdoctor.data.api

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class UserQuotaRepository(
    private val supabaseClient: SupabaseClient
) {

    companion object {
        private const val TAG = "UserQuotaRepo"
        private const val TABLE_NAME = "user_quotas"
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * Checks/fetches the current user's quota in the user_quotas table.
     * - If no row exists, creates one with daily_count = 0.
     * - If last_scan_date is not today, resets daily_count to 0 and updates the date.
     * - Returns the full UserQuotaDto after any necessary resets.
     */
    suspend fun checkQuota(): UserQuotaDto {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw Exception("User is not logged in. Cannot check quota.")

        val today = LocalDate.now().format(DATE_FORMATTER)

        try {
            val existingRows = supabaseClient
                .from(TABLE_NAME)
                .select {
                    filter {
                        eq("user_id", currentUserId)
                    }
                }
                .decodeList<UserQuotaDto>()

            if (existingRows.isEmpty()) {
                // No row exists — create one
                Log.d(TAG, "No quota row found. Creating new row for user.")
                val newQuota = UserQuotaDto(
                    userId = currentUserId,
                    dailyCount = 0,
                    lastScanDate = today
                )
                supabaseClient.from(TABLE_NAME).insert(newQuota)
                return newQuota
            }

            val quota = existingRows.first()

            if (quota.lastScanDate != today) {
                // Date has changed — reset count locally and in DB
                Log.d(TAG, "Date changed (${quota.lastScanDate} -> $today). Resetting daily count.")
                val resetQuota = quota.copy(dailyCount = 0, lastScanDate = today)
                supabaseClient.from(TABLE_NAME).update(
                    {
                        set("daily_count", 0)
                        set("last_scan_date", today)
                    }
                ) {
                    filter {
                        eq("user_id", currentUserId)
                    }
                }
                return resetQuota
            }

            Log.d(TAG, "Current daily count: ${quota.dailyCount}")
            return quota

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check quota", e)
            throw e
        }
    }

    /**
     * Queries the current user's is_premium status from the user_quotas table.
     * Returns false if no row exists or the field is not set.
     */
    suspend fun isPremium(): Boolean {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw Exception("User is not logged in. Cannot check premium status.")

        try {
            val existingRows = supabaseClient
                .from(TABLE_NAME)
                .select {
                    filter {
                        eq("user_id", currentUserId)
                    }
                }
                .decodeList<UserQuotaDto>()

            val premium = existingRows.firstOrNull()?.isPremium ?: false
            Log.d(TAG, "Premium status for user: $premium")
            return premium
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check premium status", e)
            throw e
        }
    }

    /**
     * Sets is_premium = true for the currently authenticated user in the user_quotas table.
     * Creates a row if one does not already exist.
     */
    suspend fun upgradeToPremium() {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw Exception("User is not logged in. Cannot upgrade to premium.")

        val today = LocalDate.now().format(DATE_FORMATTER)

        try {
            val existingRows = supabaseClient
                .from(TABLE_NAME)
                .select {
                    filter {
                        eq("user_id", currentUserId)
                    }
                }
                .decodeList<UserQuotaDto>()

            if (existingRows.isEmpty()) {
                // Create row with is_premium = true
                supabaseClient.from(TABLE_NAME).insert(
                    UserQuotaDto(
                        userId = currentUserId,
                        dailyCount = 0,
                        lastScanDate = today,
                        isPremium = true
                    )
                )
                Log.d(TAG, "Created quota row with premium status")
            } else {
                supabaseClient.from(TABLE_NAME).update(
                    {
                        set("is_premium", true)
                    }
                ) {
                    filter {
                        eq("user_id", currentUserId)
                    }
                }
                Log.d(TAG, "Updated user to premium")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upgrade to premium", e)
            throw e
        }
    }

    /**
     * Increments the daily_count by 1 for the current user.
     * Should be called after a successful scan.
     */
    suspend fun incrementQuota() {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            ?: throw Exception("User is not logged in. Cannot increment quota.")

        val today = LocalDate.now().format(DATE_FORMATTER)

        try {
            val existingRows = supabaseClient
                .from(TABLE_NAME)
                .select {
                    filter {
                        eq("user_id", currentUserId)
                    }
                }
                .decodeList<UserQuotaDto>()

            if (existingRows.isEmpty()) {
                // Create with count = 1
                supabaseClient.from(TABLE_NAME).insert(
                    UserQuotaDto(
                        userId = currentUserId,
                        dailyCount = 1,
                        lastScanDate = today
                    )
                )
                Log.d(TAG, "Created quota row with count = 1")
                return
            }

            val quota = existingRows.first()
            val newCount = if (quota.lastScanDate == today) quota.dailyCount + 1 else 1

            supabaseClient.from(TABLE_NAME).update(
                {
                    set("daily_count", newCount)
                    set("last_scan_date", today)
                }
            ) {
                filter {
                    eq("user_id", currentUserId)
                }
            }
            Log.d(TAG, "Incremented quota to $newCount")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment quota", e)
            throw e
        }
    }
}
