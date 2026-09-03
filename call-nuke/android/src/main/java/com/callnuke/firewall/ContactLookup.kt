package com.callnuke.firewall

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

/**
 * Checks whether an incoming phone number belongs to a contact on the device.
 *
 * Uses [ContactsContract.PhoneLookup], which normalizes the number internally, so callers do
 * not need to pre-format the number.
 */
object ContactLookup {

    private const val TAG = "CallNuke"

    fun isContact(context: Context, phoneNumber: String): Boolean {
        return try {
            val uri: Uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: Exception) {
            // Fail-safe: if contacts can't be queried (e.g. permission denied), treat the
            // number as "not a known contact" and let the caller decide what to do.
            Log.w(TAG, "isContact: lookup failed for number, treating as unknown", e)
            false
        }
    }
}
