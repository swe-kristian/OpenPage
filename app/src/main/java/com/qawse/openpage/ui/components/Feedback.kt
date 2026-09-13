package com.qawse.openpage.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens

enum class BannerTone { INFO, SUCCESS, WARNING, ERROR }

/**
 * Inline banner with tone, icon and optional action. Announces itself
 * politely to accessibility services (errors are spoken without focus).
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.ic_info,
    tone: BannerTone = BannerTone.INFO,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalOpenColors.current
    val (bg, fg) = when (tone) {
        BannerTone.INFO -> colors.well to colors.onSurfaceVariant
        BannerTone.SUCCESS -> colors.successContainer to colors.onSurface
        BannerTone.WARNING -> colors.warningContainer to colors.onWarningContainer
        BannerTone.ERROR -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = bg,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM),
            modifier = Modifier.padding(horizontal = Tokens.SpaceMD, vertical = Tokens.SpaceMD),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null, // text carries the message
                tint = if (tone == BannerTone.WARNING || tone == BannerTone.ERROR) fg else colors.onSurfaceVariant,
                modifier = Modifier.size(Tokens.IconSM + 2.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (tone == BannerTone.WARNING || tone == BannerTone.ERROR) fg else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (action != null) action()
        }
    }
}

/**
 * Calm empty state: icon in a well, headline, explanation, and a single
 * primary action when one exists.
 */
@Composable
fun EmptyState(
    icon: Int,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalOpenColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Tokens.SpaceXXXL,
                end = Tokens.SpaceXXXL,
                top = Tokens.SpaceXXL,
                bottom = Tokens.SpaceXXL,
            ),
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = colors.well,
                modifier = Modifier.size(72.dp),
            ) {}
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(Tokens.IconLG + 8.dp),
            )
        }
        Spacer(Modifier.height(Tokens.SpaceLG))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Tokens.SpaceXS + 2.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Tokens.SpaceXL))
            action()
        }
    }
}

/**
 * Determinate/indeterminate progress block with an accessible,
 * live-announced status line.
 */
@Composable
fun ProgressPanel(
    label: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    detail: String? = null,
    onCancel: (() -> Unit)? = null,
    cancelLabel: String? = null,
) {
    val colors = LocalOpenColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = label
            },
    ) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = colors.accent,
                trackColor = colors.well,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(Tokens.IconLG + 28.dp),
                strokeWidth = 3.5.dp,
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(Tokens.SpaceLG))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Spacer(Modifier.height(Tokens.SpaceXS))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onCancel != null && cancelLabel != null) {
            Spacer(Modifier.height(Tokens.SpaceXL))
            SecondaryButton(text = cancelLabel, onClick = onCancel)
        }
    }
}

/**
 * Confirmation dialog: title, calm body, explicit actions. Used for
 * irreversible or media-consuming steps.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    destructive: Boolean = false,
) {
    val colors = LocalOpenColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        },
        text = {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) colors.error else colors.accent,
                )
            }
        },
        dismissButton = dismissLabel?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
        },
        containerColor = colors.elevatedSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    )
}
