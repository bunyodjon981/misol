package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.LogLevel
import com.example.engine.TerminalLogEntry
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SlateSurfaceHigh
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextSecondaryDark

@Composable
fun TerminalConsoleCard(
    logs: List<TerminalLogEntry>,
    isRunning: Boolean,
    onClearLogs: () -> Unit,
    onLogsCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("build_terminal")
            .border(1.dp, OutlineDark, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = TerminalSurface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Terminal Header Chrome
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateSurfaceHigh.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(ErrorCoral)
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(AmberAccent)
                        )
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = CyanSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.terminal_title),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (logs.isNotEmpty()) {
                                val text = logs.joinToString("\n") {
                                    "[${it.timestamp}] ${it.message}"
                                }
                                clipboard.setText(AnnotatedString(text))
                                onLogsCopied()
                            }
                        },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.copy_logs),
                            tint = if (logs.isNotEmpty()) TextSecondaryDark else TextSecondaryDark.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClearLogs,
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_logs),
                            tint = if (logs.isNotEmpty()) TextSecondaryDark else TextSecondaryDark.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Terminal Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 170.dp, max = 280.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (logs.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$ ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = EmeraldPrimary
                        )
                        Text(
                            text = stringResource(R.string.terminal_empty),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = TextSecondaryDark
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(logs, key = { it.id }) { entry ->
                            TerminalLogRow(entry = entry)
                        }
                        if (isRunning) {
                            item(key = "active_cursor") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "$ ",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = EmeraldPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(14.dp)
                                            .alpha(cursorAlpha)
                                            .background(EmeraldPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLogRow(entry: TerminalLogEntry) {
    val msgColor: Color = when (entry.level) {
        LogLevel.SUCCESS -> EmeraldPrimary
        LogLevel.STEP -> CyanSecondary
        LogLevel.WARN -> AmberAccent
        LogLevel.ERROR -> ErrorCoral
        LogLevel.INFO -> Color(0xFFD1D9E6)
    }

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF5D708A))) {
                append("[${entry.timestamp}] ")
            }
            withStyle(
                SpanStyle(
                    color = msgColor,
                    fontWeight = if (entry.level == LogLevel.STEP || entry.level == LogLevel.SUCCESS) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Normal
                    }
                )
            ) {
                append(entry.message)
            }
        },
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.5.sp,
            lineHeight = 16.5.sp
        )
    )
}
