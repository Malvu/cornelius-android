package com.cornelius.satellite.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Les couleurs de Cornelius : fond très sombre et vert d'eau, comme l'anneau du miroir. */
object CouleursCornelius {
    val Fond = Color(0xFF06110F)
    val FondClair = Color(0xFF0C1F1B)
    val Accent = Color(0xFF00FFB4)
    val AccentFaible = Color(0xFF1E7A62)
    val Texte = Color(0xFFE6F5EF)
    val TexteFaible = Color(0xFF8FAFA6)
    val Alerte = Color(0xFFFF7A6B)
}

private val schema = darkColorScheme(
    primary = CouleursCornelius.Accent,
    onPrimary = CouleursCornelius.Fond,
    secondary = CouleursCornelius.AccentFaible,
    background = CouleursCornelius.Fond,
    onBackground = CouleursCornelius.Texte,
    surface = CouleursCornelius.FondClair,
    onSurface = CouleursCornelius.Texte,
    error = CouleursCornelius.Alerte,
)

private val typographie = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Light),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ThemeCornelius(contenu: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schema, typography = typographie, content = contenu)
}
