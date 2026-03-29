package yokai.domain

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yokai.i18n.MR

typealias ComposableDialog = (@Composable () -> Unit)?
typealias ComposableDialogState = MutableState<ComposableDialog>

/**
 * Builder to build a simple prompt alert dialog
 *
 * @property title the dialog's title
 * @property text the dialog's message
 * @property shouldContinue whether the next dialog on queue should be shown after this dialog is dismissed
 */
class AlertDialogBuilder {
    var title: String = ""
    var titleRes: StringResource? = null
    var text: String? = null
    var textRes: StringResource? = null
    var shouldContinue: Boolean = true
    var confirmText: String? = null
    var confirmTextRes: StringResource? = null
    var onConfirm: () -> Unit = {}
    var onCancel: () -> Unit = {}
    var onDismiss: () -> Unit = onCancel

    suspend fun build(dialogHostState: DialogHostState): Unit = dialogHostState.dialog { cont ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = titleRes?.let { stringResource(it) } ?: title,
                    fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                )
            },
            text = if (textRes == null && text == null) null else {
                {
                    Text(
                        text = textRes?.let { stringResource(it) } ?: text ?: "",
                        fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            },
            onDismissRequest = {
                onDismiss()
                if (shouldContinue) cont.resume(Unit) else cont.cancel()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                        if (shouldContinue) cont.resume(Unit) else cont.cancel()
                    }
                ) {
                    Text(
                        text = confirmText ?: confirmTextRes?.let { stringResource(it) } ?: androidx.compose.ui.res.stringResource(android.R.string.ok),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDismiss()
                    if (shouldContinue) cont.resume(Unit) else cont.cancel()
                }) {
                    Text(
                        text = stringResource(MR.strings.cancel),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                    )
                }
            },
        )
    }
}

suspend fun DialogHostState.simple(builder: AlertDialogBuilder.() -> Unit) = AlertDialogBuilder().apply { builder() }.build(this)

class DialogHostState(initial: ComposableDialog = null) : ComposableDialogState by mutableStateOf(initial) {
    val mutex = Mutex()

    fun closeDialog() {
        value = null
    }

    suspend inline fun <R> dialog(crossinline dialog: @Composable (CancellableContinuation<R>) -> Unit) = mutex.withLock {
        try {
            suspendCancellableCoroutine { cont -> value = { dialog(cont) } }
        } finally {
            closeDialog()
        }
    }
}
