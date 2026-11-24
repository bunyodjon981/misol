package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.BuildPipelineState
import com.example.engine.BuildStage
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorContainerDark
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SlateSurface

@Composable
fun AnimatedBuildStatusCard(
    state: BuildPipelineState,
    onNavigateToMyApks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = state.stage != BuildStage.IDLE || state.logs.isNotEmpty()
    AnimatedVisibility(visible = visible, modifier = modifier) {
        val animatedProgress by animateFloatAsState(
            targetValue = state.progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            label = "build_progress"
        )

        val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        val accentColor = when (state.stage) {
            BuildStage.COMPLETED -> EmeraldPrimary
            BuildStage.ERROR -> ErrorCoral
            else -> CyanSecondary
        }

        val borderColor = when (state.stage) {
            BuildStage.COMPLETED -> EmeraldPrimary.copy(alpha = 0.6f)
            BuildStage.ERROR -> ErrorCoral.copy(alpha = 0.6f)
            else -> OutlineDark
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("animated_status_card")
                .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = when (state.stage) {
                BuildStage.COMPLETED -> EmeraldContainer.copy(alpha = 0.35f)
                BuildStage.ERROR -> ErrorContainerDark.copy(alpha = 0.45f)
                else -> SlateSurface
            },
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.18f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            accentColor.copy(alpha = 0.25f),
                                            accentColor.copy(alpha = 0.10f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (state.stage) {
                                    BuildStage.COMPLETED -> Icons.Rounded.CheckCircle
                                    BuildStage.ERROR -> Icons.Rounded.ErrorOutline
                                    else -> Icons.Rounded.RocketLaunch
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state.stage) {
                                BuildStage.COMPLETED -> "Muvaffaqiyatli yakunlandi"
                                BuildStage.ERROR -> "Yig'ish to'xtatildi"
                                else -> "Jarayon davom etmoqda"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        AnimatedContent(
                            targetState = state.statusText.ifBlank { state.stage.labelUz },
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                                    (fadeOut(tween(180)) + slideOutVertically { -it / 3 })
                            },
                            label = "animated_status_text"
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = accentColor
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                // Step indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val steps = listOf(
                        "Ulanish" to 1,
                        "ZIP" to 3,
                        "Yuklash" to 4,
                        "Gradle" to 6,
                        "Tayyor" to 9
                    )
                    steps.forEach { (label, minStep) ->
                        val reached = state.stage.stepIndex >= minStep
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (reached) accentColor else MaterialTheme.colorScheme.outline
                                    )
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reached) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                if (state.stage == BuildStage.COMPLETED) {
                    FilledTonalButton(
                        onClick = onNavigateToMyApks,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("go_to_my_apks_button")
                    ) {
                        Text("Mening APKlarim bo'limida ko'rish")
                        Spacer(modifier = Modifier.size(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
