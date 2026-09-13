package com.qawse.openpage.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.ui.components.ToolbarIconButton
import com.qawse.openpage.ui.navigation.WindowWidth
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared screen scaffold: a headed column with optional back button,
 * centered to the readable width on medium/expanded windows.
 */
@Composable
fun ScreenScaffold(
    title: String,
    width: WindowWidth,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val hPad = if (width == WindowWidth.COMPACT) 0.dp else Tokens.SpaceLG
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = if (width == WindowWidth.COMPACT) Dp.Unspecified else Tokens.ContentMaxWidth)
                .padding(horizontal = hPad),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = if (onBack != null) Tokens.SpaceXS else Tokens.SpaceLG + Tokens.SpaceXS,
                    end = Tokens.SpaceSM,
                    top = Tokens.SpaceSM,
                    bottom = Tokens.SpaceSM,
                ),
            ) {
                if (onBack != null) {
                    ToolbarIconButton(
                        icon = R.drawable.ic_arrow_back,
                        label = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(Tokens.SpaceXS))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = LocalOpenColors.current.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

/**
 * Locale-aware, human time for job rows: relative for the recent past,
 * clock time otherwise. Pure formatting, cheap in composition.
 */
object TimeText {
    fun forTimestamp(now: Long, timestamp: Long): String {
        val diff = now - timestamp
        val minutes = diff / 60_000L
        return when {
            diff < 0 || minutes < 1 -> SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date(timestamp))
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                .format(Date(timestamp))
        }
    }
}
