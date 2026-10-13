package com.k4music.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.k4music.desktop.ui.K4Theme
import com.k4music.desktop.ui.Shell
import org.jetbrains.compose.resources.painterResource
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.app_name
import com.k4music.desktop.res.ic_launcher_foreground
import org.jetbrains.compose.resources.stringResource
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (!SingleInstance.acquire(args) { /* links from a second launch: handled once playback lands */ }) exitProcess(0)

    application {
        val state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.ic_launcher_foreground)
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            K4Theme { Shell() }
        }
    }
}
