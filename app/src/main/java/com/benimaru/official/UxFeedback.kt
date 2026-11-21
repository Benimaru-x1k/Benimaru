package com.benimaru.official

import android.os.Build
import android.view.HapticFeedbackConstants
import com.google.android.material.snackbar.Snackbar


internal class TweakUndo(
    val command: () -> String,
    val after: () -> Unit = {}
)

internal fun MainActivity.hapticConfirm() {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    window.decorView.performHapticFeedback(type)
}

internal fun MainActivity.hapticTick() {
    window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

internal fun MainActivity.showUndoSnackbar(message: String, undo: TweakUndo) {
    Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG)
        .setAnchorView(R.id.floatingBottomBar)
        .setAction(R.string.btn_undo) {
            runAdbCommand(undo.command(), getString(R.string.msg_undone)) {
                undo.after()
                fetchSystemStatuses()
            }
        }
        .show()
}
