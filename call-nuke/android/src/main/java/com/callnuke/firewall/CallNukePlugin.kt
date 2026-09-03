package com.callnuke.firewall

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONArray

/**
 * Godot <-> Android bridge for CallNuke.
 *
 * Lets the Godot UI request the CALL_SCREENING role, check whether it currently holds that
 * role, manage the manual number allowlist, toggle call protection, manage the READ_CONTACTS
 * permission, and read the blocked-calls counter maintained by [CallNukeScreeningService].
 */
class CallNukePlugin(godot: Godot) : GodotPlugin(godot) {

    companion object {
        private const val TAG = "CallNuke"
        private const val REQUEST_CODE_CALL_SCREENING_ROLE = 4242
        private const val REQUEST_CODE_CONTACTS_PERMISSION = 4243
        private const val REQUEST_CODE_SMS_PERMISSION = 4244
    }

    override fun getPluginName(): String = "CallNuke"

    /**
     * Opens the Android system UI that lets the user designate this app as the
     * call screening app. Requires API 29+ (RoleManager).
     */
    @UsedByGodot
    fun requestCallScreeningRole() {
        val activity: Activity? = activity
        if (activity == null) {
            Log.w(TAG, "requestCallScreeningRole: no activity available")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "requestCallScreeningRole: requires API 29+, current is ${Build.VERSION.SDK_INT}")
            return
        }

        val roleManager = activity.getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Log.w(TAG, "requestCallScreeningRole: ROLE_CALL_SCREENING not available on this device")
            return
        }

        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Log.i(TAG, "requestCallScreeningRole: role already held")
            return
        }

        val intent: Intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        activity.startActivityForResult(intent, REQUEST_CODE_CALL_SCREENING_ROLE)
    }

    /**
     * Returns whether this app currently holds the CALL_SCREENING role.
     * Always false below API 29.
     */
    @UsedByGodot
    fun hasCallScreeningRole(): Boolean {
        val activity: Activity = activity ?: return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        val roleManager = activity.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    /**
     * Returns the number of calls blocked (today), as tracked in SharedPreferences by the
     * CallScreeningService.
     */
    @UsedByGodot
    fun getBlockedCallsCount(): Int {
        val activity: Activity = activity ?: return 0
        return CallNukePrefs.getBlockedCallsCount(activity)
    }

    /** Adds [number] (normalized internally) to the manual allowlist. */
    @UsedByGodot
    fun addAllowedNumber(number: String) {
        activity?.let { CallNukePrefs.addAllowedNumber(it, number) }
    }

    /** Removes any stored number matching [number] from the manual allowlist. */
    @UsedByGodot
    fun removeAllowedNumber(number: String) {
        activity?.let { CallNukePrefs.removeAllowedNumber(it, number) }
    }

    /** Returns the manual allowlist as a JSON array string, e.g. `["+528112345678"]`. */
    @UsedByGodot
    fun getAllowedNumbers(): String {
        val numbers = activity?.let { CallNukePrefs.getAllowedNumbers(it) } ?: emptyList()
        return JSONArray(numbers).toString()
    }

    @UsedByGodot
    fun setCallProtectionEnabled(enabled: Boolean) {
        activity?.let { CallNukePrefs.setCallProtectionEnabled(it, enabled) }
    }

    @UsedByGodot
    fun isCallProtectionEnabled(): Boolean {
        return activity?.let { CallNukePrefs.isCallProtectionEnabled(it) } ?: true
    }

    /** Requests the READ_CONTACTS runtime permission if it isn't already granted. */
    @UsedByGodot
    fun requestContactsPermission() {
        val activity: Activity = activity ?: run {
            Log.w(TAG, "requestContactsPermission: no activity available")
            return
        }

        if (hasContactsPermission()) {
            Log.i(TAG, "requestContactsPermission: already granted")
            return
        }

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.READ_CONTACTS),
            REQUEST_CODE_CONTACTS_PERMISSION
        )
    }

    /** Returns whether READ_CONTACTS is currently granted. */
    @UsedByGodot
    fun hasContactsPermission(): Boolean {
        val activity: Activity = activity ?: return false
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ---- SMS filtering ---------------------------------------------------------------------

    @UsedByGodot
    fun setSmsProtectionEnabled(enabled: Boolean) {
        activity?.let { CallNukePrefs.setSmsProtectionEnabled(it, enabled) }
    }

    @UsedByGodot
    fun isSmsProtectionEnabled(): Boolean {
        return activity?.let { CallNukePrefs.isSmsProtectionEnabled(it) } ?: true
    }

    /** Adds [keyword] to the trusted SMS keyword list (e.g. "Steam", "Google", a bank name). */
    @UsedByGodot
    fun addTrustedKeyword(keyword: String) {
        activity?.let { CallNukePrefs.addTrustedKeyword(it, keyword) }
    }

    /** Removes any stored keyword matching [keyword] (case-insensitive) from the trusted list. */
    @UsedByGodot
    fun removeTrustedKeyword(keyword: String) {
        activity?.let { CallNukePrefs.removeTrustedKeyword(it, keyword) }
    }

    /** Returns the trusted SMS keyword list as a JSON array string, e.g. `["Steam","Google"]`. */
    @UsedByGodot
    fun getTrustedKeywords(): String {
        val keywords = activity?.let { CallNukePrefs.getTrustedKeywords(it) } ?: emptyList()
        return JSONArray(keywords).toString()
    }

    /**
     * Returns the number of SMS messages filtered (categorized as untrusted) today.
     * See [SmsReceiver] for what "filtered" actually means on this platform — it is
     * categorization, not deletion or true blocking.
     */
    @UsedByGodot
    fun getFilteredSmsCount(): Int {
        val activity: Activity = activity ?: return 0
        return CallNukePrefs.getFilteredSmsCount(activity)
    }

    /** Returns whether RECEIVE_SMS is currently granted. */
    @UsedByGodot
    fun hasSmsPermission(): Boolean {
        val activity: Activity = activity ?: return false
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Requests the RECEIVE_SMS runtime permission if it isn't already granted. */
    @UsedByGodot
    fun requestSmsPermission() {
        val activity: Activity = activity ?: run {
            Log.w(TAG, "requestSmsPermission: no activity available")
            return
        }

        if (hasSmsPermission()) {
            Log.i(TAG, "requestSmsPermission: already granted")
            return
        }

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECEIVE_SMS),
            REQUEST_CODE_SMS_PERMISSION
        )
    }
}
