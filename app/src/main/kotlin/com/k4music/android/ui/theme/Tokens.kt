package com.k4music.android.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ponytail: single source of truth for spacing/sizing; no CompositionLocal,
// no runtime cost, no recomposition on read.
object K4Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val edge = 16.dp
}

object K4Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val artworkLg = 24.dp
    val pill = 999.dp
}

object K4Size {
    val touchTarget = 48.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val artworkXs = 40.dp
    val artworkSm = 48.dp
    val artworkMd = 56.dp
    val artworkLg = 96.dp
    val rowMin = 56.dp
    val miniPlayer = 64.dp
    val progressBar = 2.dp
}

object K4Elevation {
    val none = 0.dp
    val hairline = 1.dp
    val raised = 3.dp
    val floating = 8.dp
}

// ponytail: animation timings as consts; standard easing is the common case,
// fast/slow are just duration variants of the same curve.
object K4Motion {
    const val FAST_MS = 150
    const val BASE_MS = 220
    const val SLOW_MS = 320
}

object K4Type {
    val trackingTight = (-0.2).sp
    val trackingLabel = 0.4.sp
}
