package com.jegly.www.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Light-first themes.
 *
 * Every theme this app shipped before these was dark — Catppuccin defaults to Mocha, Dracula and
 * Ptyxis have no light variant at all — so "light mode" in practice meant Material You's dynamic
 * light scheme and nothing else. These three are the deliberate answer to that, and they are all
 * *paper* palettes rather than white ones: a tinted, low-contrast ground with dark ink on it, which
 * is what makes a light theme comfortable to read a browser in rather than a flashlight.
 *
 * They are also picked to not overlap. Rosé Pine Dawn is cool rose over off-white, Everforest is
 * warm green over cream, Kanagawa Lotus is ochre parchment with woodblock-ink accents. Solarized
 * Light is the obvious fourth and is deliberately absent — it's the one light palette everyone has
 * already seen, and it sits between Everforest and Lotus without adding a third character.
 *
 * All hexes are the upstream project values, not approximations.
 */
private data class PaperPalette(
    val displayName: String,
    /** Window and page ground. */
    val base: Color,
    /** Bars, cards, dialogs — a step off [base] so chrome separates from content without a border. */
    val surface: Color,
    /** Inset controls: the omnibox pill, chip backgrounds. */
    val surfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    /** Primary ink. */
    val text: Color,
    /** Secondary ink — captions, the URL under a title. */
    val subtext: Color,
    /** Which accent doubles as the error colour, so refusals stay inside the palette. */
    val errorAccent: String,
    val defaultAccent: String,
    /**
     * Accent wheel, in hue order. Order is load-bearing: [paperColorScheme] takes secondary and
     * tertiary as the next two entries after whichever accent is selected, which is what keeps a
     * chosen accent's supporting colours hue-adjacent without a hand-maintained pairing table like
     * Catppuccin's.
     */
    val accents: Map<String, Pair<String, Color>>
)

// ---------------------------------------------------------------------------
// Rosé Pine Dawn — https://rosepinetheme.com (the "Dawn" light variant)
// ---------------------------------------------------------------------------
private val ROSE_PINE_DAWN = PaperPalette(
    displayName = "Rosé Pine",
    base = Color(0xFFFAF4ED),
    surface = Color(0xFFFFFAF3),
    surfaceVariant = Color(0xFFF2E9E1),
    outline = Color(0xFF9893A5),
    outlineVariant = Color(0xFFDFDAD9),
    text = Color(0xFF575279),
    subtext = Color(0xFF797593),
    errorAccent = "love",
    defaultAccent = "iris",
    accents = linkedMapOf(
        "love" to ("Love" to Color(0xFFB4637A)),
        "rose" to ("Rose" to Color(0xFFD7827E)),
        "gold" to ("Gold" to Color(0xFFEA9D34)),
        "pine" to ("Pine" to Color(0xFF286983)),
        "foam" to ("Foam" to Color(0xFF56949F)),
        "iris" to ("Iris" to Color(0xFF907AA9))
    )
)

// ---------------------------------------------------------------------------
// Everforest Light — https://github.com/sainnhe/everforest (medium background)
// ---------------------------------------------------------------------------
private val EVERFOREST_LIGHT = PaperPalette(
    displayName = "Everforest",
    base = Color(0xFFFDF6E3),
    surface = Color(0xFFF4F0D9),
    surfaceVariant = Color(0xFFEFEBD4),
    outline = Color(0xFF939F91),
    outlineVariant = Color(0xFFE0DCC7),
    text = Color(0xFF5C6A72),
    subtext = Color(0xFF829181),
    errorAccent = "red",
    defaultAccent = "green",
    accents = linkedMapOf(
        "red" to ("Red" to Color(0xFFF85552)),
        "orange" to ("Orange" to Color(0xFFF57D26)),
        "yellow" to ("Yellow" to Color(0xFFDFA000)),
        "green" to ("Green" to Color(0xFF8DA101)),
        "aqua" to ("Aqua" to Color(0xFF35A77C)),
        "blue" to ("Blue" to Color(0xFF3A94C5)),
        "purple" to ("Purple" to Color(0xFFDF69BA))
    )
)

// ---------------------------------------------------------------------------
// Kanagawa Lotus — https://github.com/rebelot/kanagawa.nvim (the "Lotus" light variant)
// ---------------------------------------------------------------------------
private val KANAGAWA_LOTUS = PaperPalette(
    displayName = "Kanagawa",
    base = Color(0xFFF2ECBC),
    surface = Color(0xFFE7DBA0),
    surfaceVariant = Color(0xFFE4D794),
    outline = Color(0xFF8A8980),
    outlineVariant = Color(0xFFDCD7BA),
    text = Color(0xFF545464),
    subtext = Color(0xFF716E61),
    errorAccent = "red",
    defaultAccent = "blue",
    accents = linkedMapOf(
        "red" to ("Red" to Color(0xFFC84053)),
        "pink" to ("Pink" to Color(0xFFB35B79)),
        "violet" to ("Violet" to Color(0xFF624C83)),
        "blue" to ("Blue" to Color(0xFF4D699B)),
        "teal" to ("Teal" to Color(0xFF597B75)),
        "green" to ("Green" to Color(0xFF6F894E)),
        "orange" to ("Orange" to Color(0xFFCC6D00))
    )
)

private val PAPER_PALETTES: Map<String, PaperPalette> = linkedMapOf(
    "rosepine" to ROSE_PINE_DAWN,
    "everforest" to EVERFOREST_LIGHT,
    "kanagawa" to KANAGAWA_LOTUS
)

/** key to display name, in picker order. */
val PAPER_THEMES: List<Pair<String, String>> =
    PAPER_PALETTES.map { (key, palette) -> key to palette.displayName }

fun isPaperTheme(themeKey: String): Boolean = themeKey in PAPER_PALETTES

/** Accent swatches for a paper theme; empty for anything else, so the caller can stay unbranched. */
fun paperAccentsFor(themeKey: String): Map<String, Pair<String, Color>> =
    PAPER_PALETTES[themeKey]?.accents ?: emptyMap()

fun paperDefaultAccent(themeKey: String): String =
    PAPER_PALETTES[themeKey]?.defaultAccent ?: ""

/**
 * The theme's ground colour as an ARGB int, for MainActivity's window background — null when the
 * key isn't a paper theme. Without this the window would flash plain white behind a cream palette
 * on every cold start, which is the same class of artefact the black/white split there already
 * exists to avoid.
 */
fun paperBaseArgb(themeKey: String): Int? = PAPER_PALETTES[themeKey]?.base?.toArgb()

/**
 * Builds the Material scheme. Always [lightColorScheme] — these three have no dark variant here on
 * purpose. Rosé Pine and Kanagawa both ship dark siblings upstream (Main/Moon, Wave/Dragon) and
 * adding them would double the picker for palettes that overlap heavily with Catppuccin, which the
 * app already has four flavours of.
 */
fun paperColorScheme(themeKey: String, accentKey: String): ColorScheme {
    val p = PAPER_PALETTES[themeKey] ?: ROSE_PINE_DAWN
    val keys = p.accents.keys.toList()

    val index = keys.indexOf(accentKey)
        .takeIf { it >= 0 }
        ?: keys.indexOf(p.defaultAccent).coerceAtLeast(0)

    val primary = p.accents.getValue(keys[index]).second
    val secondary = p.accents.getValue(keys[(index + 1) % keys.size]).second
    val tertiary = p.accents.getValue(keys[(index + 2) % keys.size]).second
    val error = p.accents[p.errorAccent]?.second ?: primary

    // Light accents (gold, yellow) fail against white but are fine against the palette's own ink,
    // so the on-colour follows the swatch rather than being fixed to one or the other.
    fun onAccent(c: Color) = if (c.luminance() < 0.45f) Color.White else p.text
    fun container(c: Color) = lerp(p.surface, c, 0.18f)
    fun onContainer(c: Color) = lerp(p.text, c, 0.25f)

    return lightColorScheme(
        primary              = primary,
        onPrimary            = onAccent(primary),
        primaryContainer     = container(primary),
        onPrimaryContainer   = onContainer(primary),
        secondary            = secondary,
        onSecondary          = onAccent(secondary),
        secondaryContainer   = container(secondary),
        onSecondaryContainer = onContainer(secondary),
        tertiary             = tertiary,
        onTertiary           = onAccent(tertiary),
        tertiaryContainer    = container(tertiary),
        onTertiaryContainer  = onContainer(tertiary),
        error                = error,
        onError              = onAccent(error),
        errorContainer       = container(error),
        onErrorContainer     = onContainer(error),
        background           = p.base,
        onBackground         = p.text,
        surface              = p.surface,
        onSurface            = p.text,
        surfaceVariant       = p.surfaceVariant,
        onSurfaceVariant     = p.subtext,
        outline              = p.outline,
        outlineVariant       = p.outlineVariant,
        scrim                = lerp(p.text, Color.Black, 0.5f),
        inverseSurface       = p.text,
        inverseOnSurface     = p.base,
        inversePrimary       = lerp(p.base, primary, 0.7f)
    )
}
