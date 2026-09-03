package com.callnuke.firewall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Receives incoming SMS via the [Telephony.Sms.Intents.SMS_RECEIVED_ACTION] broadcast and
 * applies CallNuke's SMS filtering rules.
 *
 * ## IMPORTANT — what this can and cannot actually do on modern Android
 *
 * This receiver is registered with `RECEIVE_SMS`, NOT as the default SMS app. On Android
 * 4.4 (KitKat) and later, only the device's default SMS app (Google Messages, Samsung
 * Messages, etc.) writes incoming messages to the SMS content provider and posts the
 * notification the user actually sees. That still happens through a separate delivery
 * path that this receiver has no access to and cannot suppress.
 *
 * Concretely:
 *  - This receiver DOES get invoked for every incoming SMS (assuming RECEIVE_SMS is granted
 *    and, on Android 6+, not restricted), and CAN read the sender and body to make a
 *    filtering decision.
 *  - Calling `abortBroadcast()` on an "untrusted" message only stops the broadcast from
 *    reaching OTHER non-default-SMS apps that also registered for SMS_RECEIVED_ACTION at a
 *    lower priority. It has NO effect on the default SMS app, which does not rely on this
 *    broadcast to receive messages, and it does NOT delete, hide, or suppress the message
 *    or its notification there.
 *  - Therefore, "filtering" here means CATEGORIZATION, not true blocking: CallNuke observes
 *    the message, decides trusted vs. untrusted, and tracks a filtered-SMS count. The
 *    message will still appear, in full, in the user's normal messaging app.
 *  - True SMS blocking (removing the message before the user ever sees it) is only possible
 *    by becoming the device's default SMS app (`RoleManager.ROLE_SMS`), which requires the
 *    app to implement full SMS send/receive/store/display functionality. That is out of
 *    scope for this MVP and is not implemented here — see the Phase 3 work order for the
 *    rationale.
 *
 * OTP/2FA safety: because this receiver never deletes or drops messages, OTP/2FA codes are
 * never at risk of being "destroyed" by this feature, regardless of keyword configuration.
 * The trusted-keyword list only affects the filtered/allowed categorization and the
 * filtered-SMS count, not whether the user actually receives the message.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallNuke"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val appContext = context.applicationContext

        if (!CallNukePrefs.isSmsProtectionEnabled(appContext)) {
            Log.i(TAG, "onReceive: SMS protection disabled, taking no action")
            return
        }

        val smsMessages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "onReceive: failed to parse SMS messages from intent", e)
            return
        }

        if (smsMessages.isNullOrEmpty()) return

        // A single SMS_RECEIVED_ACTION broadcast can carry multiple PDUs that together form
        // one multipart message from the same sender — concatenate the bodies before applying
        // the filtering decision so keyword matching sees the full text.
        val sender = smsMessages.first().originatingAddress ?: run {
            Log.i(TAG, "onReceive: no originating address, taking no action (fail-safe)")
            return
        }
        val body = smsMessages.joinToString(separator = "") { it.messageBody ?: "" }

        val decision = classify(appContext, sender, body)
        Log.i(TAG, "onReceive: sms from $sender classified as $decision")

        if (decision == Decision.FILTERED) {
            CallNukePrefs.incrementFilteredSmsCount(appContext)

            // See class doc: this does NOT prevent the default SMS app from receiving and
            // displaying the message. It only stops lower-priority, non-default-SMS-app
            // receivers (if any) from also seeing this broadcast.
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
        }

        // Do not retain the message body beyond this call — only the numeric filtered count
        // is persisted, never message content.
    }

    private enum class Decision { ALLOWED, FILTERED }

    private fun classify(context: Context, sender: String, body: String): Decision {
        // 1. Known device contact -> allowed.
        if (ContactLookup.isContact(context, sender)) {
            return Decision.ALLOWED
        }

        // 2. On the manual allowlist. NOTE (MVP): the allowlist is shared between calls and
        // SMS — a number added to unblock calls is also treated as trusted for SMS, and
        // vice versa. This keeps the UI and mental model simple for Phase 3; a separate
        // per-channel allowlist can be introduced later if needed.
        if (CallNukePrefs.isNumberAllowed(context, sender)) {
            return Decision.ALLOWED
        }

        // 3. Message body contains a user-configured trusted keyword (e.g. "Steam", "Google",
        // a bank name) -> allowed. This is what keeps OTP/2FA messages from services the user
        // trusts from being flagged as filtered.
        val keywords = CallNukePrefs.getTrustedKeywords(context)
        if (KeywordMatcher.containsTrustedKeyword(body, keywords)) {
            return Decision.ALLOWED
        }

        return Decision.FILTERED
    }
}
