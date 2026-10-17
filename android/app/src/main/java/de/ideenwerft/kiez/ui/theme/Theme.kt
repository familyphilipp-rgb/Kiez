package de.ideenwerft.kiez.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ideenwerft.kiez.R

// Farben der Ideenwerft Friedenau
val IdeenwerftRed = Color(0xFFCD1818)
val IdeenwerftRedDark = Color(0xFFE04545)

@Immutable
data class KiezColors(
    val background: Color,
    val card: Color,
    val muted: Color,
    val mutedText: Color,
    val border: Color,
    val text: Color,
    val primary: Color,
    val primarySoft: Color,
    val glass: Color,
    val isDark: Boolean,
)

private val Light = KiezColors(
    background = Color(0xFFF5F7FA), card = Color.White, muted = Color(0xFFECEFF3), mutedText = Color(0xFF5B6475),
    border = Color(0xFFE1E5EB), text = Color(0xFF22262E), primary = IdeenwerftRed, primarySoft = IdeenwerftRed.copy(alpha = 0.09f),
    glass = Color(0xF2F7F9FB), isDark = false,
)
private val Dark = KiezColors(
    background = Color(0xFF0F1218), card = Color(0xFF181C24), muted = Color(0xFF232833), mutedText = Color(0xFFA5AEBD),
    border = Color(0xFF2A303B), text = Color(0xFFE9EDF2), primary = IdeenwerftRedDark, primarySoft = IdeenwerftRedDark.copy(alpha = 0.16f),
    glass = Color(0xF0141820), isDark = true,
)

val LocalKiez = staticCompositionLocalOf { Light }

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_regular, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.sg() = copy(fontFamily = SpaceGrotesk)
    return Typography(
        displayLarge = base.displayLarge.sg(), displayMedium = base.displayMedium.sg(), displaySmall = base.displaySmall.sg(),
        headlineLarge = base.headlineLarge.sg().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.sg().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.sg().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.sg().copy(fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.sg().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.sg().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.sg().copy(fontSize = 17.sp, lineHeight = 24.sp), bodyMedium = base.bodyMedium.sg(), bodySmall = base.bodySmall.sg(),
        labelLarge = base.labelLarge.sg().copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium.sg().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.sg().copy(fontWeight = FontWeight.Bold),
    )
}

// Kleine rote Überschrift in Großbuchstaben, wie auf der Ideenwerft-Website
val Eyebrow = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)

@Composable
fun KiezTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val k = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = k.primary, onPrimary = Color.White, background = k.background, onBackground = k.text, surface = k.card, onSurface = k.text,
        surfaceVariant = k.muted, onSurfaceVariant = k.mutedText, outline = k.border, surfaceContainerLow = k.card, surfaceContainer = k.card,
        surfaceContainerHigh = k.card,
    ) else lightColorScheme(
        primary = k.primary, onPrimary = Color.White, background = k.background, onBackground = k.text, surface = k.card, onSurface = k.text,
        surfaceVariant = k.muted, onSurfaceVariant = k.mutedText, outline = k.border, surfaceContainerLow = k.card, surfaceContainer = k.card,
        surfaceContainerHigh = k.card,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalKiez provides k) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(),
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)),
            content = content,
        )
    }
}

fun parseColor(hex: String, fallback: Color = IdeenwerftRed): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
