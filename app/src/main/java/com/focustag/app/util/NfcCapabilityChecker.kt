package com.focustag.app.util

import android.content.Context
import android.nfc.NfcAdapter
import com.focustag.app.data.model.NfcCapability

object NfcCapabilityChecker {

    fun checkNfcCapability(context: Context): NfcCapability {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return when {
            adapter == null -> NfcCapability.NFC_UNAVAILABLE
            !adapter.isEnabled -> NfcCapability.NFC_OFF
            else -> NfcCapability.NFC_READY
        }
    }
}
