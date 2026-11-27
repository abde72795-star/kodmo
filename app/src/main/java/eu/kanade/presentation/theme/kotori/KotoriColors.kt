/*
 * Adapted from Kotori (https://github.com/tailolicon/kotori), Apache-2.0.
 * Manga-only palette for Kodomo's Kotori-inspired visual layer.
 */
package eu.kanade.presentation.theme.kotori

import androidx.compose.ui.graphics.Color

object KotoriColors {
    val bgBase = Color(0xFF14101F)
    val textPrimary = Color(0xFFF3EFFB)
    val textSecondary = Color(0xFFA79FC0)
    val textMuted = Color(0xFF8D84AC)
    val glassBg = Color(0x0DFFFFFF)
    val glassBorder = Color(0x17FFFFFF)
}

data class KotoriAccent(
    val start: Color,
    val end: Color,
)

val KotoriLibraryAccent = KotoriAccent(
    start = Color(0xFF38BDF8),
    end = Color(0xFF2DD4BF),
)

val KotoriMangaAccent = KotoriAccent(
    start = Color(0xFFEC4899),
    end = Color(0xFFA78BFA),
)
