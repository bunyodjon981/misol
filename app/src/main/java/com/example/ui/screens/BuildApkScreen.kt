package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.BuildPipelineManager
import com.example.engine.BuildPipelineState
import com.example.ui.BuildFormState
import com.example.ui.components.AnimatedBuildStatusCard
import com.example.ui.components.TerminalConsoleCard
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextSecondaryDark

@Composable
fun BuildApkScreen(
    formState: BuildFormState,
    pipelineState: BuildPipelineState,
    onUsernameChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onRepoNameChanged: (String) -> Unit,
    onPrivateRepoChanged: (Boolean) -> Unit,
    onZipSelected: (Uri) -> Unit,
    onStartBuild: () -> Unit,
    onCancelBuild: () -> Unit,
    onClearLogs: () -> Unit,
    onLogsCopied: () -> Unit,
    onNavigateToMyApks: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onZipSelected(uri)
            }
        }
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner Card
            ForgeHeroBanner()

            // Credentials + Repo + ZIP Picker Form Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineDark, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = SlateSurface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = OutlineDark,
                        focusedLabelColor = EmeraldPrimary,
                        cursorColor = EmeraldPrimary,
                        focusedContainerColor = SlateSurfaceVariant.copy(alpha = 0.55f),
                        unfocusedContainerColor = SlateSurfaceVariant.copy(alpha = 0.35f)
                    )

                    // 1. GitHub Username (topmost)
                    OutlinedTextField(
                        value = formState.username,
                        onValueChange = onUsernameChanged,
                        enabled = !pipelineState.isRunning,
                        label = { Text(stringResource(R.string.github_username_label)) },
                        placeholder = { Text(stringResource(R.string.github_username_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = CyanSecondary
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_username_input")
                    )

                    // 2. GitHub Token / Password (below username)
                    OutlinedTextField(
                        value = formState.token,
                        onValueChange = onTokenChanged,
                        enabled = !pipelineState.isRunning,
                        label = { Text(stringResource(R.string.github_token_label)) },
                        placeholder = { Text(stringResource(R.string.github_token_placeholder)) },
                        supportingText = {
                            Text(
                                text = stringResource(R.string.github_token_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = EmeraldPrimary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = stringResource(R.string.cd_toggle_password),
                                    tint = TextSecondaryDark
                                )
                            }
                        },
                        visualTransformation = if (showToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_token_input")
                    )

                    // 3. Repository Name (below token)
                    OutlinedTextField(
                        value = formState.repoName,
                        onValueChange = onRepoNameChanged,
                        enabled = !pipelineState.isRunning,
                        label = { Text(stringResource(R.string.repo_name_label)) },
                        placeholder = { Text(stringResource(R.string.repo_name_placeholder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Source,
                                contentDescription = null,
                                tint = CyanSecondary
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("repo_name_input")
                    )

                    // Repository Visibility Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = formState.isPrivateRepo,
                            onClick = { if (!pipelineState.isRunning) onPrivateRepoChanged(true) },
                            label = { Text("Yopiq (Private)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldPrimary,
                                selectedLeadingIconColor = EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("repo_private_chip")
                        )
                        FilterChip(
                            selected = !formState.isPrivateRepo,
                            onClick = { if (!pipelineState.isRunning) onPrivateRepoChanged(false) },
                            label = { Text("Ochiq (Public)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = CyanSecondary,
                                selectedLeadingIconColor = CyanSecondary
                            ),
                            modifier = Modifier.testTag("repo_public_chip")
                        )
                    }

                    // 4. ZIP File Picker Button Card (below repo name)
                    val hasZip = formState.selectedZipUri != null
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.5.dp,
                                color = if (hasZip) EmeraldPrimary.copy(alpha = 0.7f) else CyanSecondary.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = !pipelineState.isRunning) {
                                zipPickerLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            }
                            .testTag("pick_zip_button"),
                        color = if (hasZip) {
                            EmeraldContainer.copy(alpha = 0.3f)
                        } else {
                            SlateSurfaceVariant.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (hasZip) EmeraldPrimary.copy(alpha = 0.16f)
                                        else CyanSecondary.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderZip,
                                    contentDescription = null,
                                    tint = if (hasZip) EmeraldPrimary else CyanSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasZip) {
                                        formState.selectedZipName
                                    } else {
                                        stringResource(R.string.pick_zip_button)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (hasZip) {
                                        val sizeStr = if (formState.selectedZipSizeBytes > 0) {
                                            BuildPipelineManager.formatBytes(formState.selectedZipSizeBytes)
                                        } else {
                                            "ZIP tayyor"
                                        }
                                        "$sizeStr • O'zgartirish uchun bosing"
                                    } else {
                                        stringResource(R.string.zip_not_selected)
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = if (hasZip) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily
                                    ),
                                    color = if (hasZip) EmeraldPrimary else TextSecondaryDark
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.CloudUpload,
                                contentDescription = null,
                                tint = if (hasZip) EmeraldPrimary else CyanSecondary
                            )
                        }
                    }

                    // Validation Error Banner
                    AnimatedVisibility(visible = formState.validationError != null) {
                        Surface(
                            color = ErrorCoral.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ErrorCoral.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = formState.validationError.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorCoral,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    // 5. Primary Upload & Build Action Button
                    if (pipelineState.isRunning) {
                        OutlinedButton(
                            onClick = onCancelBuild,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("cancel_build_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ErrorCoral
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.cancel_build_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Button(
                            onClick = onStartBuild,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_build_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = Color(0xFF00291B)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.start_build_button),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // 6. Animated Status Text Card (below upload button)
            AnimatedBuildStatusCard(
                state = pipelineState,
                onNavigateToMyApks = onNavigateToMyApks
            )

            // 7. Real-Time Terminal Console (below animated status)
            TerminalConsoleCard(
                logs = pipelineState.logs,
                isRunning = pipelineState.isRunning,
                onClearLogs = onClearLogs,
                onLogsCopied = onLogsCopied
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ForgeHeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, OutlineDark, RoundedCornerShape(22.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_forge_banner),
            contentDescription = stringResource(R.string.cd_hero_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianBg.copy(alpha = 0.92f),
                            ObsidianBg.copy(alpha = 0.65f),
                            ObsidianBg.copy(alpha = 0.35f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = EmeraldPrimary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.border(
                    1.dp,
                    EmeraldPrimary.copy(alpha = 0.45f),
                    RoundedCornerShape(99.dp)
                )
            ) {
                Text(
                    text = "GITHUB ACTIONS • AUTO APK",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hero_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                maxLines = 2
            )
        }
    }
}
