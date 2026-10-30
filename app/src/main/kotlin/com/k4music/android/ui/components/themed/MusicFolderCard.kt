package com.k4music.android.ui.components.themed

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.k4music.android.utils.medium
import com.k4music.android.utils.secondary
import com.k4music.android.utils.toast
import com.k4music.core.ui.LocalAppearance
import java.io.File

/** The folder K4 Audio tells people to put songs in: `Music/K4 Audio`, visible in any file manager. */
object MusicFolder {
    private const val NAME = "K4 Audio"

    fun dir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), NAME)

    /** Creates the folder; songs dropped in it are picked up by the Local library on their own. */
    fun ensure() = runCatching { dir().mkdirs() }.getOrDefault(false)

    fun open(context: Context) {
        ensure()
        val folder = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music/$NAME")
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(view)
        } catch (_: ActivityNotFoundException) {
            context.toast("Open your Files app and go to Music › $NAME")
        }
    }
}

/** Top of the Local list: the button to open the music folder and how to bring songs over from a PC. */
@Composable
fun MusicFolderCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (_, typography) = LocalAppearance.current
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MusicFolder.ensure() } }
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SecondaryTextButton(text = "Open music folder", onClick = { MusicFolder.open(context) })
        BasicText(
            text = "Put songs in Music › K4 Audio and they show up here by themselves. " +
                "From a PC: plug the phone in with USB, choose File transfer, then copy the audio files into that folder.",
            style = typography.xxs.medium.secondary
        )
    }
}
