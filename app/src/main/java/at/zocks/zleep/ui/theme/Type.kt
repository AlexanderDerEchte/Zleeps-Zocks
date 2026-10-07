package at.zocks.zleep.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/** Ziffern gleich breit, damit sich Kennzahlen beim Aktualisieren nicht verschieben. */
private const val TabularNumbers = "tnum"

internal val ZocksTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Light, fontFeatureSettings = TabularNumbers),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Light, fontFeatureSettings = TabularNumbers),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = TabularNumbers),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
)

/** Große Kennzahl, z. B. Schlafscore oder Temperatur. */
val MetricTextStyle: TextStyle = Base.displayMedium.copy(
    fontWeight = FontWeight.Light,
    fontFeatureSettings = TabularNumbers,
)

/**
 * Einzelne große Zahl, die sich nicht laufend ändert (z. B. Schlafscore). Proportionale
 * Ziffern wirken als Blickfang ruhiger als gleich breite.
 */
val HeroNumberStyle: TextStyle = Base.displayLarge.copy(fontWeight = FontWeight.Light)
