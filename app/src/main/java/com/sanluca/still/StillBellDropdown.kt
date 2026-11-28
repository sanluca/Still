package com.sanluca.still

import androidx.compose.foundation.layout.Arrangement
import com.sanluca.still.core.BellSound
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StillBellDropdown(
    selectedBell: BellSound?,
    enabled: Boolean,
    onSelectBell: (BellSound?) -> Unit,
    onPreviewBell: (BellSound) -> Unit,
    modifier: Modifier = Modifier,
    allowGlobalOption: Boolean = false,
    globalLabel: String = "",
    previewBellFallback: BellSound = BellSound.CLASSIC_BOWL
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = selectedBell?.let { stringResource(it.labelRes) }
        ?: globalLabel.ifBlank { stringResource(previewBellFallback.labelRes) }
    val previewContentDesc = stringResource(R.string.cd_preview_bell_button)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayLabel,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (expanded) "▲" else "▼",
                        fontSize = 11.sp,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                if (allowGlobalOption) {
                    val isGlobalSelected = selectedBell == null
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = globalLabel,
                                color = if (isGlobalSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isGlobalSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        trailingIcon = if (isGlobalSelected) {
                            {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            onSelectBell(null)
                        }
                    )
                }

                BellSound.entries.forEach { bell ->
                    val isSelected = selectedBell == bell
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(bell.labelRes),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        trailingIcon = if (isSelected) {
                            {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            onSelectBell(bell)
                            onPreviewBell(bell)
                        }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { onPreviewBell(selectedBell ?: previewBellFallback) },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(42.dp)
                .semantics { contentDescription = previewContentDesc }
        ) {
            Text(
                text = "▶",
                fontSize = 13.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }
            )
        }
    }
}
