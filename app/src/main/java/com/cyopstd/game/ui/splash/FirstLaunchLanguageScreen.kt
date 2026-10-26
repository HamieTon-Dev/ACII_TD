package com.cyopstd.game.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyopstd.game.i18n.Language
import com.cyopstd.game.i18n.Languages
import com.cyopstd.game.ui.common.AsciiBackdrop
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.theme.Palette
import kotlinx.coroutines.delay

/** How long each language's "choose your language" stays up before the next. */
const val TITLE_CYCLE_MS = 1600L

/**
 * First launch (owner, 2026-10-26): right after the HamieTon.dev ident and before the boot
 * screen, the player picks the game's language. Only on a fresh install; an update never shows
 * it (see `Languages.start`). Nothing here is translated on purpose: every language is listed in
 * its own name, the title cycles through all of them, and the phone's language is highlighted so
 * most players only need to tap it.
 */
@Composable
fun FirstLaunchLanguageScreen(
    deviceTag: String,
    onPick: (Language) -> Unit
) {
    val titles = remember(deviceTag) {
        val tags = listOf(deviceTag) + Languages.all.map { it.tag }.filter { it != deviceTag }
        tags.mapNotNull { Languages.chooseTitle[it] }.distinct()
    }
    var titleIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(titles) {
        while (true) {
            delay(TITLE_CYCLE_MS)
            titleIndex = (titleIndex + 1) % titles.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .testTag("first-launch-language"),
        contentAlignment = Alignment.TopCenter
    ) {
        AsciiBackdrop(modifier = Modifier.fillMaxSize(), density = 34)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = titles[titleIndex.coerceIn(titles.indices)],
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Green,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text("> LANGUAGE_", style = MaterialTheme.typography.labelSmall, color = Palette.GreenDim)
            AsciiRule(color = Palette.Cyan.copy(alpha = 0.45f))
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 190.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(Languages.all, key = { it.tag }) { language ->
                    val phone = language.tag == deviceTag
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (phone) Palette.SurfaceRaised else Palette.Surface.copy(alpha = 0.85f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (phone) Palette.Cyan else Palette.Divider,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable(role = Role.Button) { onPick(language) }
                            .testTag("first-language-${language.tag}")
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = language.nativeName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (phone) Palette.Cyan else Palette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (language.englishName != language.nativeName) {
                            Text(
                                text = language.englishName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
