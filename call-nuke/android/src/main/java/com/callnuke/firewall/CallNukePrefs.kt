package com.callnuke.firewall

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single source of truth for CallNuke configuration.
 *
 * Readable/writable both from the Godot plugin (UI thread, app process) and from the
 * CallScreeningService (which may run independently of the Godot app being alive).
 */
object CallNukePrefs {

    private const val PREFS_NAME = "callnuke_prefs"

    private const val KEY_CALL_PROTECTION_ENABLED = "call_protection_enabled"
    private const val KEY_BLOCKED_CALLS_COUNT = "blocked_calls_count"
    private const val KEY_BLOCKED_CALLS_DATE = "blocked_calls_date"
    private const val KEY_ALLOWED_NUMBERS = "allowed_numbers"

    private const val KEY_SMS_PROTECTION_ENABLED = "sms_protection_enabled"
    private const val KEY_TRUSTED_SMS_KEYWORDS = "trusted_sms_keywords"
    private const val KEY_FILTERED_SMS_COUNT = "filtered_sms_count"
    private const val KEY_FILTERED_SMS_DATE = "filtered_sms_date"

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isCallProtectionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CALL_PROTECTION_ENABLED, true)
    }

    fun setCallProtectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CALL_PROTECTION_ENABLED, enabled).apply()
    }

    fun getBlockedCallsCount(context: Context): Int {
        resetDailyCountIfNeeded(context)
        return getPrefs(context).getInt(KEY_BLOCKED_CALLS_COUNT, 0)
    }

    fun incrementBlockedCallsCount(context: Context) {
        resetDailyCountIfNeeded(context)
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_BLOCKED_CALLS_COUNT, 0)
        prefs.edit()
            .putInt(KEY_BLOCKED_CALLS_COUNT, current + 1)
            .putString(KEY_BLOCKED_CALLS_DATE, todayString())
            .apply()
    }

    fun getBlockedCallsDate(context: Context): String {
        return getPrefs(context).getString(KEY_BLOCKED_CALLS_DATE, "") ?: ""
    }

    /** Resets the daily blocked-calls counter if the stored date differs from today. */
    fun resetDailyCountIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val storedDate = prefs.getString(KEY_BLOCKED_CALLS_DATE, null)
        val today = todayString()
        if (storedDate != null && storedDate != today) {
            prefs.edit()
                .putInt(KEY_BLOCKED_CALLS_COUNT, 0)
                .putString(KEY_BLOCKED_CALLS_DATE, today)
                .apply()
        } else if (storedDate == null) {
            prefs.edit().putString(KEY_BLOCKED_CALLS_DATE, today).apply()
        }
    }

    private fun todayString(): String = DATE_FORMAT.format(Date())

    // ---- Manual allowlist ----------------------------------------------------------------

    /** Returns the manually allow-listed numbers, in the (normalized) form they were stored. */
    fun getAllowedNumbers(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_ALLOWED_NUMBERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAllowedNumbers(context: Context, numbers: List<String>) {
        val array = JSONArray(numbers)
        getPrefs(context).edit().putString(KEY_ALLOWED_NUMBERS, array.toString()).apply()
    }

    /** Normalizes and adds [number] to the manual allowlist, if not already present. */
    fun addAllowedNumber(context: Context, number: String) {
        val normalized = PhoneNumberUtils.normalize(context, number)
        val current = getAllowedNumbers(context).toMutableList()
        val alreadyPresent = current.any { PhoneNumberUtils.numbersMatch(it, normalized) }
        if (!alreadyPresent) {
            current.add(normalized)
            saveAllowedNumbers(context, current)
        }
    }

    /** Removes any stored number that matches [number] from the manual allowlist. */
    fun removeAllowedNumber(context: Context, number: String) {
        val current = getAllowedNumbers(context)
        val updated = current.filterNot { PhoneNumberUtils.numbersMatch(it, number) }
        saveAllowedNumbers(context, updated)
    }

    /** Whether [incomingNumber] matches any number on the manual allowlist. */
    fun isNumberAllowed(context: Context, incomingNumber: String): Boolean {
        return getAllowedNumbers(context).any { PhoneNumberUtils.numbersMatch(it, incomingNumber) }
    }

    // ---- SMS protection --------------------------------------------------------------------

    fun isSmsProtectionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SMS_PROTECTION_ENABLED, true)
    }

    fun setSmsProtectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SMS_PROTECTION_ENABLED, enabled).apply()
    }

    /** Returns the user-configured list of trusted SMS keywords (e.g. "Steam", "Google"). */
    fun getTrustedKeywords(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_TRUSTED_SMS_KEYWORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveTrustedKeywords(context: Context, keywords: List<String>) {
        val array = JSONArray(keywords)
        getPrefs(context).edit().putString(KEY_TRUSTED_SMS_KEYWORDS, array.toString()).apply()
    }

    /** Adds [keyword] to the trusted keyword list, if not already present (case-insensitive). */
    fun addTrustedKeyword(context: Context, keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val current = getTrustedKeywords(context).toMutableList()
        val alreadyPresent = current.any { it.equals(trimmed, ignoreCase = true) }
        if (!alreadyPresent) {
            current.add(trimmed)
            saveTrustedKeywords(context, current)
        }
    }

    /** Removes any stored keyword matching [keyword] (case-insensitive) from the trusted list. */
    fun removeTrustedKeyword(context: Context, keyword: String) {
        val current = getTrustedKeywords(context)
        val updated = current.filterNot { it.equals(keyword.trim(), ignoreCase = true) }
        saveTrustedKeywords(context, updated)
    }

    fun getFilteredSmsCount(context: Context): Int {
        resetDailyFilteredSmsCountIfNeeded(context)
        return getPrefs(context).getInt(KEY_FILTERED_SMS_COUNT, 0)
    }

    fun incrementFilteredSmsCount(context: Context) {
        resetDailyFilteredSmsCountIfNeeded(context)
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_FILTERED_SMS_COUNT, 0)
        prefs.edit()
            .putInt(KEY_FILTERED_SMS_COUNT, current + 1)
            .putString(KEY_FILTERED_SMS_DATE, todayString())
            .apply()
    }

    /** Resets the daily filtered-SMS counter if the stored date differs from today. */
    private fun resetDailyFilteredSmsCountIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val storedDate = prefs.getString(KEY_FILTERED_SMS_DATE, null)
        val today = todayString()
        if (storedDate != null && storedDate != today) {
            prefs.edit()
                .putInt(KEY_FILTERED_SMS_COUNT, 0)
                .putString(KEY_FILTERED_SMS_DATE, today)
                .apply()
        } else if (storedDate == null) {
            prefs.edit().putString(KEY_FILTERED_SMS_DATE, today).apply()
        }
    }
}
