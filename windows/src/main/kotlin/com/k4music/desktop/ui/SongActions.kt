package com.k4music.desktop.ui

import androidx.compose.runtime.Composable
import com.k4music.desktop.player.Track
import com.k4music.desktop.res.Res
import com.k4music.desktop.data.LibraryHooks
import com.k4music.desktop.player.Downloads
import com.k4music.desktop.res.add
import com.k4music.desktop.res.add_to_playlist
import com.k4music.desktop.res.desktop_copy_link
import com.k4music.desktop.res.desktop_unlike
import com.k4music.desktop.res.desktop_download_cancel
import com.k4music.desktop.res.download
import com.k4music.desktop.res.heart
import com.k4music.desktop.res.heart_outline
import com.k4music.desktop.res.like
import com.k4music.desktop.res.remove_download
import com.k4music.desktop.res.trash
import com.k4music.desktop.res.format_go_to_artist
import com.k4music.desktop.res.disc
import com.k4music.desktop.res.enqueue
import com.k4music.desktop.res.go_to_album
import com.k4music.desktop.res.person
import com.k4music.desktop.res.play_next
import com.k4music.desktop.res.radio
import com.k4music.desktop.res.start_radio
import com.k4music.desktop.res.play_skip_forward
import com.k4music.desktop.res.share_social
import org.jetbrains.compose.resources.stringResource
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * The standard song menu, shared by every list (same items as the Android song menu).
 * [extra] adds screen-specific items such as "Remove from queue".
 */
@Composable
fun songActions(extra: (Track) -> List<MenuAction> = { emptyList() }): (Track) -> List<MenuAction> {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val playNext = stringResource(Res.string.play_next)
    val startRadio = stringResource(Res.string.start_radio)
    val enqueue = stringResource(Res.string.enqueue)
    val goToAlbum = stringResource(Res.string.go_to_album)
    val copyLink = stringResource(Res.string.desktop_copy_link)
    val moreFrom = stringResource(Res.string.format_go_to_artist)
    val like = stringResource(Res.string.like)
    val unlike = stringResource(Res.string.desktop_unlike)
    val addToPlaylist = stringResource(Res.string.add_to_playlist)
    val cancelDownload = stringResource(Res.string.desktop_download_cancel)
    val download = stringResource(Res.string.download)
    val removeDownload = stringResource(Res.string.remove_download)

    return { track ->
        buildList {
            if (!track.isLocal && !track.isSaavn) add(MenuAction(startRadio, Res.drawable.radio) { player.playRadio(track) })
            add(MenuAction(playNext, Res.drawable.play_skip_forward) { player.playNext(track) })
            add(MenuAction(enqueue, Res.drawable.enqueue) { player.addToQueue(listOf(track)) })
            val liked = track.id in LibraryHooks.liked.value
            add(MenuAction(if (liked) unlike else like, if (liked) Res.drawable.heart else Res.drawable.heart_outline) { LibraryHooks.toggleLike(track) })
            add(MenuAction(addToPlaylist, Res.drawable.add) { AppDialogs.addToPlaylist(listOf(track)) })
            if (!track.isLocal) {
                if (track.id in Downloads.progress.value) add(MenuAction(cancelDownload, Res.drawable.trash) { Downloads.cancel(track.id) })
                else if (Downloads.isDownloaded(track.id)) add(MenuAction(removeDownload, Res.drawable.trash) { Downloads.delete(track.id) })
                else add(MenuAction(download, Res.drawable.download) { Downloads.download(track) })
            }
            addAll(extra(track))
            track.albumId?.let { id -> add(MenuAction(goToAlbum, Res.drawable.disc) { navigator.go(Screen.Album(id)) }) }
            track.artistIds.firstOrNull()?.let { id ->
                val name = track.artists?.substringBefore(",")?.substringBefore("&")?.trim().orEmpty()
                add(MenuAction(moreFrom.replace("%1\$s", name), Res.drawable.person) { navigator.go(Screen.Artist(id)) })
            }
            if (!track.isLocal && !track.isSaavn) add(MenuAction(copyLink, Res.drawable.share_social) {
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(StringSelection("https://music.youtube.com/watch?v=${track.id}"), null)
            })
        }
    }
}
