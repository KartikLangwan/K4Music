package com.k4music.android.ui.components.themed

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.k4music.android.preferences.UIStatePreferences
import com.k4music.android.utils.hasPermission
import com.k4music.app.R
import com.k4music.core.ui.utils.isAtLeastAndroid13

/** First launch only: explains why notifications and audio access are wanted, then asks. Both are optional. */
@Composable
fun PermissionIntro() {
    val context = LocalContext.current
    val missing = remember {
        buildList {
            if (isAtLeastAndroid13) add(Manifest.permission.POST_NOTIFICATIONS)
            add(
                if (isAtLeastAndroid13) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }.filterNot(context::hasPermission)
    }
    var show by remember { mutableStateOf(missing.isNotEmpty() && !UIStatePreferences.permissionsIntroShown) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    if (show) ConfirmationDialog(
        text = stringResource(R.string.permission_intro),
        cancelText = stringResource(R.string.permission_intro_skip),
        confirmText = stringResource(R.string.permission_intro_continue),
        onDismiss = {
            show = false
            UIStatePreferences.permissionsIntroShown = true
        },
        onConfirm = { launcher.launch(missing.toTypedArray()) }
    )
}
