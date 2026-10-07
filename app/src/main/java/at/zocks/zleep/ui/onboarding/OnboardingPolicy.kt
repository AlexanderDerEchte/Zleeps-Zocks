package at.zocks.zleep.ui.onboarding

/**
 * Ob die Einrichtung beim ersten Start angeboten wird. In UI-Tests abgeschaltet, damit sie
 * direkt auf der Startseite beginnen (eigener Test für die Einrichtung).
 */
fun interface OnboardingPolicy {
    fun offerOnboarding(): Boolean
}
