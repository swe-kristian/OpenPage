package com.qawse.openpage.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.ui.theme.rememberMotionDuration

/**
 * The one obvious primary action. Ink-filled, 52 dp tall, with an honest
 * loading state: when [loading] is true the label stays but a spinner takes
 * the icon's place and presses are ignored.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: Int? = null,
) {
    val colors = LocalOpenColors.current
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.disabled,
            disabledContentColor = colors.onDisabled,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Tokens.IconMD),
                strokeWidth = 2.5.dp,
                color = colors.onPrimary,
            )
            Spacer(Modifier.width(Tokens.SpaceSM))
        } else if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(Tokens.IconMD),
            )
            Spacer(Modifier.width(Tokens.SpaceSM))
        }
        androidx.compose.material3.Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled || loading) colors.onPrimary else colors.onDisabled,
        )
    }
}

/** Quiet secondary action — outlined, same height, same rhythm. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    val colors = LocalOpenColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.onSurface,
            disabledContentColor = colors.onDisabled,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (enabled) colors.divider else colors.disabled),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(Tokens.IconMD),
            )
            Spacer(Modifier.width(Tokens.SpaceSM))
        }
        androidx.compose.material3.Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onSurface else colors.onDisabled,
        )
    }
}

/** Small tinted action chip (e.g. per-printer actions). 48 dp target. */
@Composable
fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    prominent: Boolean = false,
) {
    val colors = LocalOpenColors.current
    val duration = rememberMotionDuration(Tokens.MotionFast)
    val bg by animateColorAsState(
        if (prominent) colors.primary else colors.well,
        tween(duration), label = "chipBg")
    val fg by animateColorAsState(
        if (prominent) colors.onPrimary else colors.onSurfaceVariant,
        tween(duration), label = "chipFg")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = bg,
        contentColor = fg,
        modifier = modifier.heightIn(min = Tokens.TouchMin),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXS),
            modifier = Modifier.padding(horizontal = Tokens.SpaceMD, vertical = Tokens.SpaceSM),
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(Tokens.IconSM),
                )
            }
            androidx.compose.material3.Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        }
    }
}

/**
 * Square icon button meeting the 48 dp minimum target; the visual glyph
 * stays 24 dp. [label] becomes the content description.
 */
@Composable
fun OpenIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    prominent: Boolean = false,
) {
    val colors = LocalOpenColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (prominent) colors.primary else colors.well,
        contentColor = if (prominent) colors.onPrimary else tint,
        modifier = modifier.size(Tokens.TouchMin),
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(Tokens.IconLG),
            )
        }
    }
}

/** Icon + label button used for toolbars (back, settings…). */
@Composable
fun ToolbarIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.size(Tokens.TouchMin)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(Tokens.IconLG),
        )
    }
}

/** Progress spinner with an accessible label, used in full-screen waits. */
@Composable
fun LabeledSpinner(label: String, modifier: Modifier = Modifier) {
    val colors = LocalOpenColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMD),
        modifier = modifier.semantics { contentDescription = label },
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Tokens.IconLG + 20.dp),
            strokeWidth = 3.dp,
            color = colors.primary,
        )
    }
}
