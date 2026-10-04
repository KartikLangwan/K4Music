package com.k4music.android.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.k4music.core.ui.LocalAppearance

@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = ripple(bounded = false)
) {
    val (colorPalette) = LocalAppearance.current

    HeaderIconButton(
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        indication = indication,
        enabled = enabled,
        color = if (enabled) colorPalette.text else colorPalette.textDisabled
    )
}

@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = ripple(bounded = false)
) = IconButton(
    icon = icon,
    color = color,
    onClick = onClick,
    enabled = enabled,
    indication = indication,
    modifier = modifier
        .padding(all = 4.dp)
        .size(18.dp)
)

@Composable
fun IconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = ripple(bounded = false)
) {
    val (colorPalette) = LocalAppearance.current

    IconButton(
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        indication = indication,
        enabled = enabled,
        color = if (enabled) colorPalette.text else colorPalette.textDisabled
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = ripple(bounded = false)
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val palette = LocalAppearance.current.colorPalette
    Image(
    painter = painterResource(icon),
    contentDescription = null,
    colorFilter = ColorFilter.tint(if (pressed && enabled) palette.accent else color),
    modifier = Modifier
        .clickable(
            indication = indication,
            interactionSource = interaction,
            enabled = enabled,
            onClick = onClick
        )
        .then(modifier)
        .clip(CircleShape)
        .background(if (pressed && enabled) palette.accent.copy(alpha = 0.16f) else Color.Transparent)
    )
}
