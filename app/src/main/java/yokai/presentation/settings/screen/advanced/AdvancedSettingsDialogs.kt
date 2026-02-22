package yokai.presentation.settings.screen.advanced

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import yokai.domain.DialogHostState
import yokai.i18n.MR
import android.R as AR

suspend fun DialogHostState.awaitCheckForBetaPrompt(
    isEnabled: Boolean,
    onConfirm: () -> Unit = {},
): Unit = dialog { cont ->
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(MR.strings.warning),
                fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
            )
        },
        text = {
            Text(
                text = stringResource(if (isEnabled) MR.strings.warning_enroll_into_beta else MR.strings.warning_unenroll_from_beta),
                fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        },
        onDismissRequest = { cont.cancel() },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    cont.cancel()
                }
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(AR.string.ok),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { cont.cancel() }) {
                Text(
                    text = stringResource(MR.strings.cancel),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                )
            }
        },
    )
}
