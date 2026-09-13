package com.qawse.openpage.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qawse.openpage.R
import com.qawse.openpage.ui.theme.LocalOpenColors
import com.qawse.openpage.ui.theme.Tokens
import com.qawse.openpage.ui.theme.rememberMotionDuration

/** Label above a control, with optional supporting/error line below. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(
            start = Tokens.SpaceLG + Tokens.SpaceXS,
            end = Tokens.SpaceLG + Tokens.SpaceXS,
            bottom = Tokens.SpaceSM,
        ),
    )
}

@Composable
fun FieldSupport(text: String?, error: Boolean = false, modifier: Modifier = Modifier) {
    if (text == null) return
    val colors = LocalOpenColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) colors.error else colors.onSurfaceVariant,
        modifier = modifier.padding(
            start = Tokens.SpaceLG + Tokens.SpaceXS,
            end = Tokens.SpaceLG + Tokens.SpaceXS,
            top = Tokens.SpaceXS + 2.dp,
        ),
    )
}

/**
 * Segmented single-choice control. One implementation replaces the three
 * ad-hoc chip rows of v1: proper radio semantics for TalkBack, animated
 * selection that respects reduced motion, 48 dp targets, graceful wrap
 * for long labels via weight distribution.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenColors.current
    val duration = rememberMotionDuration(Tokens.MotionFast)
    val selectedText = stringResource(R.string.a11y_selected)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSM),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS),
    ) {
        options.forEach { option ->
            val active = option == selected
            val optionDesc = label(option)
            val bg by animateColorAsState(
                if (active) colors.primary else colors.well, tween(duration), label = "segBg")
            val fg by animateColorAsState(
                if (active) colors.onPrimary else colors.onSurfaceVariant, tween(duration), label = "segFg")
            val borderColor by animateColorAsState(
                if (active) colors.primary else colors.divider, tween(duration), label = "segBorder")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Tokens.TouchMin)
                    .background(bg, MaterialTheme.shapes.small)
                    .border(1.dp, borderColor, MaterialTheme.shapes.small)
                    .selectable(
                        selected = active,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    )
                    .padding(horizontal = Tokens.SpaceXS, vertical = Tokens.SpaceSM),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        contentDescription = optionDesc +
                            if (active) " · $selectedText" else ""
                    },
                )
            }
        }
    }
}

/** Label + segmented control block. */
@Composable
fun <T> SettingSegment(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FieldLabel(title)
        SegmentedControl(
            options = options,
            selected = selected,
            onSelect = onSelect,
            label = label,
        )
    }
}

/** Copies stepper: − value +. Buttons carry descriptions; the value is
 *  announced as “Copies: N”. */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    valueLabel: String,
    range: IntRange = 1..50,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMD),
        modifier = modifier.padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS),
    ) {
        StepButton(
            icon = R.drawable.ic_remove,
            enabled = value > range.first,
            label = decreaseLabel,
        ) { onChange((value - 1).coerceIn(range)) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(48.dp)
                .semantics { contentDescription = "$valueLabel: $value" },
        )
        StepButton(
            icon = R.drawable.ic_add,
            enabled = value < range.last,
            label = increaseLabel,
        ) { onChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(icon: Int, enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = LocalOpenColors.current
    Box(
        modifier = Modifier
            .size(Tokens.TouchMin)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) colors.onSurface else colors.onDisabled,
            modifier = Modifier.size(Tokens.IconMD),
        )
    }
}

/**
 * Dropdown picker built on ExposedDropdownMenuBox: real text-field
 * semantics, keyboard navigable, checkmark on the selected row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    value: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fieldLabel: String? = null,
) {
    val colors = LocalOpenColors.current
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(horizontal = Tokens.SpaceLG + Tokens.SpaceXS)) {
        if (fieldLabel != null) {
            FieldLabel(fieldLabel, modifier = Modifier.padding(0.dp))
        }
        ExposedDropdownMenuBox(
            expanded = open,
            onExpandedChange = { open = it },
        ) {
            OutlinedTextField(
                value = label(value),
                onValueChange = {},
                readOnly = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(Tokens.IconMD),
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.divider,
                    unfocusedContainerColor = colors.surface,
                    focusedContainerColor = colors.surface,
                    disabledBorderColor = colors.divider,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(label(opt), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { onSelect(opt); open = false },
                        leadingIcon = if (opt == value) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = stringResource(R.string.a11y_selected),
                                    tint = colors.accent,
                                    modifier = Modifier.size(Tokens.IconMD),
                                )
                            }
                        } else null,
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

/**
 * Outlined text field with label, supporting text and error styling —
 * the app’s single text entry component.
 */
@Composable
fun OpenTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    numeric: Boolean = false,
    singleLine: Boolean = true,
) {
    val colors = LocalOpenColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        shape = MaterialTheme.shapes.small,
        isError = isError,
        label = label?.let { { Text(it) } },
        supportingText = supportingText?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        keyboardOptions = if (numeric)
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        else KeyboardOptions.Default,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorBorderColor = colors.error,
            errorSupportingTextColor = colors.error,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
