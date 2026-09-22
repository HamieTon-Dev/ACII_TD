package com.packetbastion.asciidefense.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.ui.theme.Palette

/**
 * Shared frame for every non-game screen: a titled header with a BACK control,
 * then the content. Keeping this in one place is what makes the menus feel like
 * one application rather than six.
 */
@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    backgroundAnimation: Boolean = true,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        AsciiBackdrop(
            modifier = Modifier.fillMaxSize(),
            enabled = backgroundAnimation,
            density = 26
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Palette.Cyan
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary
                        )
                    }
                }
                actions?.invoke()
                Spacer(Modifier.height(0.dp))
                CompactButton(
                    text = "< BACK",
                    onClick = onBack,
                    accent = Palette.TextSecondary
                )
            }

            Spacer(Modifier.height(6.dp))
            AsciiRule(color = Palette.CyanDim)
            Spacer(Modifier.height(10.dp))

            content()
        }
    }
}
