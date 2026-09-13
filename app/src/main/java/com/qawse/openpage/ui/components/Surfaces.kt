package com.qawse.openpage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.SectionTracking
import com.qawse.openpage.ui.theme.Tokens

/** Section label: uppercase, tracked, quiet. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val colors = LocalOpenColors.current
    androidx.compose.material3.Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = SectionTracking),
        color = colors.onSurfaceVariant,
        modifier = modifier.padding(
            start = Tokens.SpaceLG + Tokens.SpaceXS,
            end = Tokens.SpaceLG + Tokens.SpaceXS,
            top = Tokens.SpaceMD,
            bottom = Tokens.SpaceSM,
        ),
    )
}

/**
 * The primary surface: hairline border, subtle tonal step, RadiusMD.
 * Pure — callers own padding and spacing.
 */
@Composable
fun OpenCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {},
) {
    val colors = LocalOpenColors.current
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Tokens.SpaceLG),
                content = content,
            )
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Tokens.SpaceLG),
                content = content,
            )
        }
    }
}

/** Label/value detail row for diagnostics and printer details. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalOpenColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMD),
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        androidx.compose.material3.Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(128.dp),
        )
        androidx.compose.material3.Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class PillTone { NEUTRAL, SUCCESS, WARNING, ERROR }

/**
 * Status pill — icon AND text AND tone, never color alone.
 */
@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    icon: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenColors.current
    val (fg, bg) = when (tone) {
        PillTone.SUCCESS -> colors.success to colors.successContainer
        PillTone.WARNING -> colors.warning to colors.warningContainer
        PillTone.ERROR -> colors.error to colors.errorContainer
        PillTone.NEUTRAL -> colors.onSurfaceVariant to colors.well
    }
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = bg,
        contentColor = fg,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXS),
            modifier = Modifier.padding(horizontal = Tokens.SpaceSM + 2.dp, vertical = 5.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null, // the text carries the meaning
                modifier = Modifier.size(12.dp),
            )
            androidx.compose.material3.Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
        }
    }
}

/** Row with leading icon badge, title, supporting text, trailing slot. */
@Composable
fun ListRow(
    icon: Int,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    OpenCard(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon)
            Spacer(Modifier.width(Tokens.SpaceMD + 2.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.size(2.dp))
                    androidx.compose.material3.Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Tokens.SpaceSM))
            trailing()
        }
    }
}

/** Round monochrome icon badge used for list rows and empty states. */
@Composable
fun IconBadge(
    icon: Int,
    modifier: Modifier = Modifier,
    size: Int = 44,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bg: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size.dp)
            .background(bg, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5).dp),
        )
    }
}

/** Switch row on a card — setting with title, optional explanation. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    OpenCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.size(2.dp))
                    androidx.compose.material3.Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(Tokens.SpaceMD))
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
