package com.looper.benchsuite.ui.component

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looper.android.support.util.IntentUtils
import com.looper.benchsuite.R
import com.looper.benchsuite.ui.theme.AppTheme

@Composable
fun BenchmarkPreferenceComponent(
    context: Context,
    title: String,
    summary: String,
    sourceLink: String,
    benchmarkType: String,
    onRunIconClick: () -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, Color.Black),
    ) {
        Row(
            modifier = Modifier
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_play_circle),
                contentDescription = stringResource(R.string.run),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onRunIconClick() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.info),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { showDialog.value = true }
            )
        }
    }

    if (showDialog.value) {
        BenchmarkInfoDialog(
            context = context,
            sourceLink = sourceLink,
            benchmarkType = benchmarkType,
            onDismissRequest = { showDialog.value = false }
        )
    }
}

@Composable
fun BenchmarkInfoDialog(
    context: Context,
    sourceLink: String,
    benchmarkType: String,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.okay))
            }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.source) + ":")
                    Text(
                        text = stringResource(R.string.click_here),
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            IntentUtils.openURL(context, sourceLink)
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.benchmark) + ":")
                    Text(benchmarkType)
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun BenchmarkPreferenceComponentPreview() {
    AppTheme {
        BenchmarkPreferenceComponent(
            context = LocalContext.current,
            title = "Preference Title",
            summary = "This is the summary of the preference.",
            sourceLink = "https://example.com",
            benchmarkType = "Example",
            onRunIconClick = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BenchmarkInfoDialogPreview() {
    val showDialog = remember { mutableStateOf(false) }

    AppTheme {
        if (showDialog.value) {
            BenchmarkInfoDialog(
                context = LocalContext.current,
                sourceLink = "https://example.com",
                benchmarkType = "Example",
                onDismissRequest = { showDialog.value = false }
            )
        }
    }
}
