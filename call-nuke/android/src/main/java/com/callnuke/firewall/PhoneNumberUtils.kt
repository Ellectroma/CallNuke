package com.callnuke.firewall

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Phone number comparison and normalization helpers, built entirely on top of
 * [android.telephony.PhoneNumberUtils] (Android's own implementation). No custom parsing.
 */
object PhoneNumberUtils {

    private const val TAG = "CallNuke"

    /**
     * Compares two phone numbers for equality using Android's built-in comparison, which
     * accounts for differing formatting, country codes, and local vs international format.
     */
    fun numbersMatch(a: String, b: String): Boolean {
        return android.telephony.PhoneNumberUtils.compare(a, b)
    }

    /**
     * Normalizes a number for storage/display: strips non-dialable characters, then attempts
     * to format it as E.164 using the device's current country ISO. Falls back to the
     * stripped (but non-E164) number if that isn't possible.
     */
    fun normalize(context: Context, number: String): String {
        val stripped = try {
            android.telephony.PhoneNumberUtils.normalizeNumber(number)
        } catch (e: Exception) {
            null
        } ?: number

        val countryIso = try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val networkIso = tm?.networkCountryIso
            val simIso = tm?.simCountryIso
            when {
                !networkIso.isNullOrEmpty() -> networkIso
                !simIso.isNullOrEmpty() -> simIso
                else -> null
            }
        } catch (e: Exception) {
            null
        }

        return try {
            if (!countryIso.isNullOrEmpty()) {
                val e164 = android.telephony.PhoneNumberUtils.formatNumberToE164(
                    stripped,
                    countryIso.uppercase()
                )
                e164 ?: stripped
            } else {
                stripped
            }
        } catch (e: Exception) {
            Log.w(TAG, "normalize: E164 formatting failed, falling back to stripped number", e)
            stripped
        }
    }
}
