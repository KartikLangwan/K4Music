package com.k4music.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k4music.android.ui.components.GlassPanel
import com.k4music.android.utils.isInPip
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance
import kotlinx.coroutines.delay

// The service retries and switches source within a few hundred ms; only a failure that outlives this shows.
private const val ERROR_GRACE_MS = 1_200L

/** Playback failures keep a visible, labelled recovery action above the artwork. */
@Composable
fun PlaybackError(
    isDisplayed: Boolean,
    messageProvider: @Composable () -> String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppearance.current.colorPalette
    val pip = isInPip()
    val visible by produceState(false, isDisplayed) {
        if (isDisplayed) delay(ERROR_GRACE_MS)
        value = isDisplayed
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassPanel(elevation = 0.dp, backgroundAlpha = 0.96f) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        messageProvider(),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text,
                        textAlign = TextAlign.Center,
                        maxLines = if (pip) 2 else 6
                    )
                    if (!pip) TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.retry), color = palette.accent)
                    }
                }
            }
        }
    }
}
