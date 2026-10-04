package com.k4music.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.android.ui.components.FadingRow
import com.k4music.android.utils.medium
import com.k4music.android.utils.semiBold
import androidx.compose.ui.unit.sp
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.shimmer
import kotlin.random.Random

@Composable
fun Header(
    title: String,
    modifier: Modifier = Modifier,
    actionsContent: @Composable RowScope.() -> Unit = {}
) = Header(
    modifier = modifier,
    titleContent = {
        FadingRow {
            BasicText(
                text = title,
                style = LocalAppearance.current.typography.xxl.semiBold.copy(fontSize = 26.sp),
                maxLines = 1
            )
        }
    },
    actionsContent = actionsContent
)

@Composable
fun Header(
    titleContent: @Composable () -> Unit,
    actionsContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) = Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .fillMaxWidth()
) {
    titleContent()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .heightIn(min = 40.dp),
        content = actionsContent
    )
}

@Composable
fun HeaderPlaceholder(modifier: Modifier = Modifier) = Box(
    contentAlignment = Alignment.CenterEnd,
    modifier = modifier
        .padding(horizontal = 16.dp)
        .height(Dimensions.items.headerHeight)
        .fillMaxWidth()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val text = remember { List(Random.nextInt(4, 16)) { " " }.joinToString(separator = "") }

    Box(
        modifier = Modifier
            .background(colorPalette.shimmer)
            .fillMaxWidth(remember { 0.25f + Random.nextFloat() * 0.5f })
    ) {
        BasicText(
            text = text,
            style = typography.xxl.medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
