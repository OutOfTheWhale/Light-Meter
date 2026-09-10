package com.outofthewhale.lightmeter

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

enum class LightVariant { Readout, Title, Heading, Copy, Button, Detail, Fine, Micro }

/**
 * Text in the Light Phone's voice: one typeface, no colour beyond white and the
 * single secondary grey, and hierarchy carried entirely by size.
 */
@Composable
fun LightText(
    text: String,
    variant: LightVariant,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    lighten: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val type = LightTokens.type
    val style: TextStyle = when (variant) {
        LightVariant.Readout -> type.readout
        LightVariant.Title -> type.title
        LightVariant.Heading -> type.heading
        LightVariant.Copy -> type.copy
        LightVariant.Button -> type.button
        LightVariant.Detail -> type.detail
        LightVariant.Fine -> type.fine
        LightVariant.Micro -> type.micro
    }
    BasicText(
        text = text,
        style = style.copy(
            color = if (lighten) {
                LightTokens.colors.contentSecondary
            } else {
                LightTokens.colors.content
            },
            textAlign = align ?: TextAlign.Unspecified,
        ),
        maxLines = maxLines,
        modifier = modifier,
    )
}

/** Tap targets with no ripple - LightOS does not use them. */
@Composable
fun Modifier.lightClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}
