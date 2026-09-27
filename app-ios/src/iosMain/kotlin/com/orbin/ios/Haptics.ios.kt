package com.orbin.ios

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

internal actual fun triggerLightHaptic() {
    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
}

internal actual fun triggerMediumHaptic() {
    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
}

internal actual fun triggerSuccessHaptic() {
    UINotificationFeedbackGenerator().notificationOccurred(
        UINotificationFeedbackType.UINotificationFeedbackTypeSuccess,
    )
}
