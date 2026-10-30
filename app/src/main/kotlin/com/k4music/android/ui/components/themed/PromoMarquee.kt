package com.k4music.android.ui.components.themed

import com.k4music.android.ui.theme.brandBrush
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.android.Brand
import com.k4music.app.R
import com.k4music.core.ui.LocalAppearance

/** Looping, white glowing "made by" strip for Home. Text taps open Instagram, the button the website. */
@Composable
fun PromoMarquee(modifier: Modifier = Modifier) {
    val palette = LocalAppearance.current.colorPalette
    val uriHandler = LocalUriHandler.current
    val glow by rememberInfiniteTransition(label = "promo-glow").animateFloat(
        initialValue = 6f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "promo-glow-radius"
    )
    val text = listOf(
        stringResource(R.string.promo_made_by, Brand.INSTAGRAM_HANDLE),
        Brand.WEBSITE_URL.removePrefix("https://"),
        "Instagram @${Brand.INSTAGRAM_HANDLE}",
        stringResource(R.string.promo_features)
    ).joinToString("   •   ")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(color = Color.White, offset = Offset.Zero, blurRadius = glow)
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(Color(0xCC141414))
                .clickable { runCatching { uriHandler.openUri(Brand.INSTAGRAM_URL) } }
                .padding(horizontal = 14.dp, vertical = 7.dp)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 0,
                    repeatDelayMillis = 0,
                    spacing = MarqueeSpacing(12.dp),
                    velocity = 70.dp
                )
        )
        Text(
            text = stringResource(R.string.promo_visit),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .clickable { runCatching { uriHandler.openUri(Brand.WEBSITE_URL) } }
                .background(brandBrush(palette.accent))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
