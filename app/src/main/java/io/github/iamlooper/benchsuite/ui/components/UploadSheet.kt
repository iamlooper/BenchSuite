package io.github.iamlooper.benchsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.iamlooper.benchsuite.R

/**
 * Modal bottom sheet shown before submitting a run to the global leaderboard.
 * User enters an optional display name (blank → "Anonymous").
 *
 * @param onSubmit(displayName) Called when user confirms; [displayName] may be empty.
 * @param onDismiss Called when user dismisses without submitting.
 * @param isUploading When true, the submit button shows a disabled/loading state.
 * @param initialDisplayName Pre-populated display name from Settings (default empty).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSheet(
    onSubmit: (displayName: String) -> Unit,
    onDismiss: () -> Unit,
    isUploading: Boolean = false,
    initialDisplayName: String = "",
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    var displayName by rememberSaveable(initialDisplayName) { mutableStateOf(initialDisplayName) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isUploading) onDismiss()
        },
        sheetState       = sheetState,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text  = stringResource(R.string.upload_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text  = stringResource(R.string.upload_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppMetricChip(text = stringResource(R.string.upload_sheet_opt_in_chip))
                AppMetricChip(
                    text = stringResource(R.string.upload_sheet_anonymous_chip),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.86f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value         = displayName,
                onValueChange = { if (it.length <= 50) displayName = it },
                label         = { Text(stringResource(R.string.upload_sheet_name_label)) },
                placeholder   = { Text(stringResource(R.string.common_anonymous)) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction      = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                FilledTonalButton(
                    onClick  = onDismiss,
                    enabled  = !isUploading,
                    shape    = MaterialTheme.shapes.extraLarge,
                ) { Text(stringResource(R.string.common_cancel)) }

                Button(
                    onClick  = { onSubmit(displayName.trim()) },
                    enabled  = !isUploading,
                    shape    = MaterialTheme.shapes.extraLarge,
                ) { Text(if (isUploading) stringResource(R.string.upload_sheet_uploading) else stringResource(R.string.upload_sheet_submit)) }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
