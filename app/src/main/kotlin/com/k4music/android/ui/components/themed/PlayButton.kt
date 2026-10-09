package com.k4music.android.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.utils.forcePlayFromBeginning
import com.k4music.android.utils.semiBold
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance

/** Big accent "Play" pill under a collection's artwork: replaces the queue and plays from the first song. */
@Composable
fun BigPlayButton(
    items: () -> List<MediaItem>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    val (colorPalette, typography) = LocalAppearance.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(52.dp)
            // Soft accent glow: coloured shadow on Android 9+, a plain soft shadow below that.
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = CircleShape,
                ambientColor = colorPalette.accent,
                spotColor = colorPalette.accent
            )
            .clip(CircleShape)
            .background(if (enabled) colorPalette.accent else colorPalette.accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button) {
                val list = items()
                if (list.isEmpty()) return@clickable
                binder?.stopRadio()
                binder?.player?.forcePlayFromBeginning(list)
            }
    ) {
        Image(
            painter = painterResource(R.drawable.play),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette.onAccent),
            modifier = Modifier.size(20.dp)
        )
        BasicText(
            text = stringResource(R.string.play),
            style = typography.s.semiBold.copy(color = colorPalette.onAccent)
        )
    }
}

/** Header "Shuffle" next to Enqueue: replaces the queue with [items] in random order. */
@Composable
fun ShuffleButton(
    items: () -> List<MediaItem>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    SecondaryTextButton(
        text = stringResource(R.string.shuffle),
        enabled = enabled,
        modifier = modifier,
        onClick = {
            val list = items()
            if (list.isEmpty()) return@SecondaryTextButton
            binder?.stopRadio()
            binder?.player?.forcePlayFromBeginning(list.shuffled())
        }
    )
}
