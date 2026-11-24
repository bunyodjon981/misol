package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ApkRecordEntity
import com.example.engine.BuildPipelineManager
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OutlineDark
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyApksScreen(
    apks: List<ApkRecordEntity>,
    onDownloadApk: (ApkRecordEntity) -> Unit,
    onShareApk: (ApkRecordEntity) -> Unit,
    onDeleteApk: (ApkRecordEntity) -> Unit,
    onNavigateToBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<ApkRecordEntity?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (apks.isEmpty()) {
            EmptyApksState(
                onCreateClick = onNavigateToBuild,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .padding(24.dp)
            )
        } else {
            val totalSizeBytes = remember(apks) { apks.sumOf { it.fileSizeBytes } }
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "vault_summary") {
                    VaultSummaryCard(
                        totalCount = apks.size,
                        totalBytes = totalSizeBytes
                    )
                }

                items(apks, key = { it.id }) { apk ->
                    ApkRecordCard(
                        apk = apk,
                        onDownload = { onDownloadApk(apk) },
                        onShare = { onShareApk(apk) },
                        onDeleteRequest = { pendingDelete = apk }
                    )
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = {
                Text("${record.fileName} — ${stringResource(R.string.delete_confirm_message)}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteApk(record)
                        pendingDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.confirm_yes),
                        color = ErrorCoral
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_no))
                }
            }
        )
    }
}

@Composable
private fun VaultSummaryCard(totalCount: Int, totalBytes: Long) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineDark, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = SlateSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Tayyor APKlar ombori",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ustiga bosib qurilmaga yuklab olishingiz mumkin",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }
            Surface(
                color = EmeraldContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "$totalCount ta • ${BuildPipelineManager.formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ApkRecordCard(
    apk: ApkRecordEntity,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dateFormatter = remember {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }
    val formattedDate = remember(apk.createdAtMillis) {
        dateFormatter.format(Date(apk.createdAtMillis))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, OutlineDark, RoundedCornerShape(20.dp))
            .clickable(onClick = onDownload)
            .testTag("apk_item_card"),
        shape = RoundedCornerShape(20.dp),
        color = SlateSurface,
        tonalElevation = 3.dp
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
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(EmeraldContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apk.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = apk.fileName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = CyanSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDeleteRequest,
                    modifier = Modifier.testTag("delete_apk_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_apk),
                        tint = TextSecondaryDark
                    )
                }
            }

            // Metadata chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetadataBadge(text = "${apk.ownerUsername}/${apk.repoName}")
                MetadataBadge(text = BuildPipelineManager.formatBytes(apk.fileSizeBytes))
                MetadataBadge(text = formattedDate)
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_apk_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldPrimary,
                        contentColor = Color(0xFF00291B)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.download_apk),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("share_apk_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.share_apk))
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String) {
    Surface(
        color = SlateSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyApksState(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_vault),
            contentDescription = stringResource(R.string.cd_empty_vault),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(190.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, OutlineDark, RoundedCornerShape(24.dp))
        )
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.empty_apks_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_apks_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryDark,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onCreateClick,
            modifier = Modifier
                .height(50.dp)
                .testTag("empty_state_create_apk_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldPrimary,
                contentColor = Color(0xFF00291B)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.RocketLaunch,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.empty_apks_cta),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
