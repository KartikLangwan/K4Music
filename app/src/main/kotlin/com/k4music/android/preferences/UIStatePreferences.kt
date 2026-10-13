package com.k4music.android.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.k4music.android.GlobalPreferencesHolder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object UIStatePreferences : GlobalPreferencesHolder() {
    private var storedHomeScreenTabIndex by int(0, name = "homeScreenTabIndex")
    var homeScreenTabIndex: Int
        get() = storedHomeScreenTabIndex
        set(value) {
            storedHomeScreenTabIndex = value
            if (value >= 2) libraryTabIndex = value
        }

    /** The Library tab the bottom bar returns to. */
    var libraryTabIndex by int(2)
    var searchResultScreenTabIndex by int(0)

    var artistScreenTabIndexProperty = int(0)
    var artistScreenTabIndex by artistScreenTabIndexProperty

    var playlistsAsGrid by boolean(true)
    var permissionsIntroShown by boolean(false)

    private var visibleTabs by json(mapOf<String, List<String>>())

    @Composable
    fun mutableTabStateOf(
        key: String,
        default: ImmutableList<String> = persistentListOf()
    ): MutableState<ImmutableList<String>> = remember(key, default, visibleTabs) {
        mutableStateOf(
            value = visibleTabs.getOrDefault(key, default).toImmutableList(),
            policy = object : SnapshotMutationPolicy<ImmutableList<String>> {
                override fun equivalent(
                    a: ImmutableList<String>,
                    b: ImmutableList<String>
                ): Boolean {
                    val eq = a == b
                    if (!eq) visibleTabs += key to b
                    return eq
                }
            }
        )
    }
}
