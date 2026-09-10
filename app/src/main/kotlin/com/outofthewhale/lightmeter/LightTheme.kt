package com.outofthewhale.lightmeter

import android.content.Context
import android.graphics.fonts.SystemFonts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The Light Phone's visual language, rebuilt for a plain Android app.
 *
 * The colour and type values mirror the Light SDK's own tokens (MIT licensed),
 * so a tool built outside the SDK still sits properly beside the ones built
 * inside it: black ground, white content, one grey for anything secondary, and
 * a type scale that leans on size rather than weight or colour for hierarchy.
 */
data class LightColors(
    val background: Color,
    val content: Color,
    val contentSecondary: Color,
)

val DarkColors = LightColors(
    background = Color.Black,
    content = Color.White,
    contentSecondary = Color(0xFFBBBBBB),
)

data class LightTypography(
    val readout: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val copy: TextStyle,
    val button: TextStyle,
    val detail: TextStyle,
    val fine: TextStyle,
    val micro: TextStyle,
)

/**
 * Akkurat is the Light Phone's typeface and it ships as a system font on the
 * LP3, so it can be loaded off the device rather than bundled - which keeps its
 * licence somebody else's problem. Anywhere else this falls back gracefully.
 */
fun lightFontFamily(): FontFamily {
    val fonts = runCatching {
        SystemFonts.getAvailableFonts()
            .filter { it.file?.name?.startsWith("Akkurat", ignoreCase = true) == true }
            .mapNotNull { font ->
                val file = font.file ?: return@mapNotNull null
                Font(
                    file = file,
                    weight = FontWeight(font.style.weight),
                    style = if (font.style.slant != 0) FontStyle.Italic else FontStyle.Normal,
                )
            }
    }.getOrDefault(emptyList())
    return if (fonts.isNotEmpty()) FontFamily(fonts) else FontFamily.SansSerif
}

private fun typographyFor(family: FontFamily) = LightTypography(
    // The SDK's Title is 115sp, which is right for a clock on an otherwise empty
    // screen. The meter has to fit a viewfinder, two wheels and a control row on
    // 486dp of height, so the answer gets its own slightly smaller size.
    readout = TextStyle(
        fontSize = 76.sp,
        fontFamily = family,
        fontWeight = FontWeight.Light,
        lineHeight = (76 * 1.05).sp,
    ),
    title = TextStyle(
        fontSize = 115.sp,
        fontFamily = family,
        fontWeight = FontWeight.Light,
        lineHeight = (115 * 1.10).sp,
    ),
    heading = TextStyle(
        fontSize = 38.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        lineHeight = (38 * 1.35).sp,
    ),
    copy = TextStyle(
        fontSize = 30.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        lineHeight = (30 * 1.50).sp,
    ),
    button = TextStyle(
        fontSize = 30.sp,
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        letterSpacing = (30 * 0.15).sp,
        lineHeight = (30 * 1.10).sp,
    ),
    detail = TextStyle(
        fontSize = 20.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        lineHeight = (20 * 1.45).sp,
    ),
    fine = TextStyle(
        fontSize = 25.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        letterSpacing = (25 * 0.03).sp,
        lineHeight = (25 * 1.15).sp,
    ),
    micro = TextStyle(
        fontSize = 16.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        letterSpacing = (16 * 0.10).sp,
        lineHeight = (16 * 1.20).sp,
    ),
)

val LocalLightColors: ProvidableCompositionLocal<LightColors> =
    staticCompositionLocalOf { DarkColors }
val LocalLightTypography = staticCompositionLocalOf { typographyFor(FontFamily.SansSerif) }

object LightTokens {
    val colors: LightColors
        @Composable get() = LocalLightColors.current

    val type: LightTypography
        @Composable get() = LocalLightTypography.current
}

@Composable
fun LightTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val context: Context = LocalContext.current
    val typography = remember { typographyFor(lightFontFamily()) }
    CompositionLocalProvider(
        LocalLightColors provides DarkColors,
        LocalLightTypography provides typography,
        content = content,
    )
}
