package com.qing.hachimi.util

import android.view.HapticFeedbackConstants
import android.view.View

enum class HapticLevel {
    Light,
    Medium,
    Strong,
}

fun View.haptic(level: HapticLevel) {
    val effect = when (level) {
        HapticLevel.Light -> HapticFeedbackConstants.CLOCK_TICK
        HapticLevel.Medium -> HapticFeedbackConstants.VIRTUAL_KEY
        HapticLevel.Strong -> HapticFeedbackConstants.CONFIRM
    }
    performHapticFeedback(effect)
}
