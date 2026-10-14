package com.k4music.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.k4music.app.R
import com.k4music.android.ui.theme.brandBrush
import com.k4music.core.ui.LocalAppearance

@Composable
fun PressFeedbackIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalAppearance.current.colorPalette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) palette.accent.copy(alpha = 0.30f) else Color.Transparent,
        tween(70), label = "player press"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(containerColor = background),
        content = content
    )
}

/** Every player action has the same visible press feedback, including momentary actions. */
@Composable
fun PlayerActionButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    toggle: Boolean = false,
    filled: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color = Color.Unspecified
) {
    val palette = LocalAppearance.current.colorPalette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Filled = the play button: the K4 ribbon gradient (as on PC), darkened while pressed.
    val background by animateColorAsState(
        if (pressed && enabled) {
            if (filled) Color.Black.copy(alpha = 0.18f) else palette.accent
        } else if (filled) Color.Transparent else if (active) palette.accent.copy(alpha = 0.14f) else Color.Transparent,
        tween(70), label = "action background"
    )
    val foreground by animateColorAsState(
        if (filled) Color.White
        else if (pressed && enabled) palette.onAccent
        else if (tint != Color.Unspecified) tint else if (active) palette.accent else palette.text,
        tween(70), label = "action foreground"
    )
    val loadingLabel = stringResource(R.string.loading_music)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(containerColor = background, contentColor = foreground),
        modifier = modifier
            .size(buttonSize)
            .then(if (filled) Modifier.background(brandBrush(palette.accent), CircleShape) else Modifier)
            .semantics { if (toggle) selected = active }
    ) {
        if (loading) CircularProgressIndicator(
            Modifier.size(iconSize).semantics { contentDescription = loadingLabel },
            color = foreground, strokeWidth = 2.dp
        ) else AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.7f)) togetherWith fadeOut(tween(90))
            },
            label = "action icon"
        ) { currentIcon ->
            Icon(painterResource(currentIcon), label, Modifier.size(iconSize), tint = foreground)
        }
    }
}
