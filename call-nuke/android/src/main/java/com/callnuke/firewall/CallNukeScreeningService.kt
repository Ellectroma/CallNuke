package com.callnuke.firewall

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * CallScreeningService implementing CallNuke's contact-based allowlist firewall.
 *
 * This service runs in its own process, independent of whether the Godot app/UI is running.
 * It makes decisions purely from SharedPreferences (via [CallNukePrefs]) and the device's
 * Contacts provider (via [ContactLookup]) — a call is allowed through if the caller is a
 * known contact or is on the manual allowlist; otherwise it is rejected.
 */
class CallNukeScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CallNuke"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val rawNumber = handle?.schemeSpecificPart

        if (rawNumber.isNullOrEmpty()) {
            // Fail safe: if we can't determine the number, allow the call through.
            Log.i(TAG, "onScreenCall: number unavailable, allowing call (fail-safe)")
            respondToCall(callDetails, allowResponse())
            return
        }

        if (!CallNukePrefs.isCallProtectionEnabled(applicationContext)) {
            Log.i(TAG, "onScreenCall: protection disabled, allowing call from $rawNumber")
            respondToCall(callDetails, allowResponse())
            return
        }

        if (ContactLookup.isContact(applicationContext, rawNumber)) {
            Log.i(TAG, "onScreenCall: $rawNumber is a known contact, allowing call")
            respondToCall(callDetails, allowResponse())
            return
        }

        if (CallNukePrefs.isNumberAllowed(applicationContext, rawNumber)) {
            Log.i(TAG, "onScreenCall: $rawNumber is on manual allowlist, allowing call")
            respondToCall(callDetails, allowResponse())
            return
        }

        Log.i(TAG, "onScreenCall: $rawNumber not allow-listed, rejecting call")
        respondToCall(callDetails, rejectResponse())
        CallNukePrefs.incrementBlockedCallsCount(applicationContext)
    }

    private fun rejectResponse(): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false) // keep in call log for debugging
            .setSkipNotification(true)
            .setSilenceCall(true)
            .build()
    }

    private fun allowResponse(): CallResponse {
        return CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .setSilenceCall(false)
            .build()
    }
}
