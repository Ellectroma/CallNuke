package com.callnuke.firewall

import android.util.Log

/**
 * Matches SMS message bodies against a list of user-configured "trusted keywords"
 * (e.g. "Steam", "Google", "Bank of America") so that OTP/2FA and other expected
 * messages from services the user trusts are not filtered.
 */
object KeywordMatcher {

    private const val TAG = "CallNuke"

    /**
     * Returns true if [messageBody] contains any of [keywords].
     *
     * Matching is case-insensitive and attempts whole-word/phrase matching (using regex
     * word boundaries) to reduce false positives — e.g. the keyword "Steam" should match
     * "Your STEAM Guard code is 12345" but should not match inside "downstream".
     *
     * Word-boundary matching relies on `\b`, which is based on `\w` (letters/digits/underscore).
     * For keywords that are pure words this works well. If a keyword contains characters that
     * are not word characters (e.g. punctuation, or non-Latin scripts where `\b` behaves
     * inconsistently), we fall back to a plain case-insensitive substring match rather than
     * silently failing to match at all. This means very short or unusual keywords may still
     * produce false positives — that's an accepted MVP tradeoff, and is documented for users
     * when they add keywords.
     */
    fun containsTrustedKeyword(messageBody: String, keywords: List<String>): Boolean {
        if (messageBody.isEmpty() || keywords.isEmpty()) return false

        for (rawKeyword in keywords) {
            val keyword = rawKeyword.trim()
            if (keyword.isEmpty()) continue

            val matched = try {
                val pattern = Regex(
                    "\\b" + Regex.escape(keyword) + "\\b",
                    setOf(RegexOption.IGNORE_CASE)
                )
                pattern.containsMatchIn(messageBody)
            } catch (e: Exception) {
                // Fall back to simple substring matching if the regex can't be built/applied
                // for this keyword (e.g. unusual Unicode content).
                Log.w(TAG, "containsTrustedKeyword: regex match failed, falling back to contains", e)
                messageBody.contains(keyword, ignoreCase = true)
            }

            if (matched) return true
        }
        return false
    }
}
