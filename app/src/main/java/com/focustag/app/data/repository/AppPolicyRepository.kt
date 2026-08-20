package com.focustag.app.data.repository

import android.content.Context
import android.content.SharedPreferences

class AppPolicyRepository(private val context: Context, private val userId: String) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("focus_policy_$userId", Context.MODE_PRIVATE)
    }

    private val KEY_BLOCKED_APPS = "blocked_apps"

    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
    }

    fun toggleAppBlock(packageName: String) {
        val currentBlocked = getBlockedApps().toMutableSet()
        if (currentBlocked.contains(packageName)) {
            currentBlocked.remove(packageName)
        } else {
            currentBlocked.add(packageName)
        }
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, currentBlocked).apply()
    }
}
