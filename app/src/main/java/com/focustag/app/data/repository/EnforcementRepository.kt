package com.focustag.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.app.admin.DevicePolicyManager
import com.focustag.app.data.model.EnforcementLedger
import com.focustag.app.data.model.EnforcementSnapshot
import com.focustag.app.data.model.EnforcementStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EnforcementRepository(private val context: Context, private val userId: String) {

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val userPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("focus_prefs_$userId", Context.MODE_PRIVATE)
    }

    private val devicePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("device_focus_state", Context.MODE_PRIVATE)
    }

    private companion object {
        const val KEY_SNAPSHOT = "enforcement_snapshot"
        const val KEY_LEDGER = "enforcement_ledger"
        const val KEY_STATUS = "enforcement_status"
        const val KEY_DEVICE_OWNER_ID = "active_enforcement_user_id"
    }

    fun getSnapshot(): EnforcementSnapshot? {
        val json = userPrefs.getString(KEY_SNAPSHOT, null) ?: return null
        return try {
            Json.decodeFromString<EnforcementSnapshot>(json)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSnapshot(snapshot: EnforcementSnapshot?) {
        userPrefs.edit().apply {
            if (snapshot == null) {
                remove(KEY_SNAPSHOT)
            } else {
                putString(KEY_SNAPSHOT, Json.encodeToString(snapshot))
            }
            apply()
        }
    }

    fun getLedger(): EnforcementLedger {
        val json = userPrefs.getString(KEY_LEDGER, null) ?: return EnforcementLedger()
        return try {
            Json.decodeFromString<EnforcementLedger>(json)
        } catch (e: Exception) {
            EnforcementLedger()
        }
    }

    fun saveLedger(ledger: EnforcementLedger) {
        userPrefs.edit().putString(KEY_LEDGER, Json.encodeToString(ledger)).apply()
    }

    fun getStatus(): EnforcementStatus {
        val name = userPrefs.getString(KEY_STATUS, EnforcementStatus.IDLE.name) ?: EnforcementStatus.IDLE.name
        return try {
            EnforcementStatus.valueOf(name)
        } catch (e: Exception) {
            EnforcementStatus.IDLE
        }
    }

    fun saveStatus(status: EnforcementStatus) {
        userPrefs.edit().putString(KEY_STATUS, status.name).apply()
    }

    // Device-level ownership
    fun getDeviceEnforcementOwnerId(): String? {
        return devicePrefs.getString(KEY_DEVICE_OWNER_ID, null)
    }

    fun setDeviceEnforcementOwnerId(ownerId: String?) {
        devicePrefs.edit().putString(KEY_DEVICE_OWNER_ID, ownerId).apply()
    }

    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }
}
