package com.k4music.android.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.k4music.app.R
import com.k4music.android.ui.components.themed.Scaffold
import com.k4music.android.ui.screens.GlobalRoutes
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.secondary
import com.k4music.compose.persist.PersistMapCleanup
import com.k4music.compose.routing.RouteHandler
import com.k4music.core.ui.LocalAppearance

@Route
@Composable
fun SearchScreen(
    initialTextInput: String,
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val [tabIndex, onTabChanged] = rememberSaveable { mutableIntStateOf(0) }

    val [textFieldValue, onTextFieldValueChanged] = rememberSaveable(
        initialTextInput,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    PersistMapCleanup(prefix = "search/")

    RouteHandler {
        GlobalRoutes()

        Content {
            val palette = LocalAppearance.current.colorPalette
            // A pill search bar shared by the online and library tabs.
            val decorationBox: @Composable (@Composable () -> Unit) -> Unit = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(palette.background1)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (textFieldValue.text.isEmpty()) BasicText(
                            text = stringResource(R.string.search_placeholder),
                            maxLines = 1,
                            style = LocalAppearance.current.typography.s.secondary
                        )

                        innerTextField()
                    }
                }
            }

            Scaffold(
                key = "search",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChange = onTabChanged,
                tabColumnContent = {
                    tab(0, R.string.online, R.drawable.globe, canHide = false)
                    tab(1, R.string.library, R.drawable.library)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> OnlineSearch(
                            textFieldValue = textFieldValue,
                            onTextFieldValueChange = onTextFieldValueChanged,
                            onSearch = onSearch,
                            onViewPlaylist = onViewPlaylist,
                            decorationBox = decorationBox,
                            focused = child == null
                        )

                        1 -> LocalSongSearch(
                            textFieldValue = textFieldValue,
                            onTextFieldValueChange = onTextFieldValueChanged,
                            decorationBox = decorationBox
                        )
                    }
                }
            }
        }
    }
}
