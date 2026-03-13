package yokai.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CombinedCircularProgressIndicator(
    progress: () -> Float,
    isInverted: () -> Boolean,
) {
    AnimatedContent(
        targetState = progress() == 0f,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "progressState",
    ) { indeterminate ->
        if (indeterminate) {
            // Indeterminate
            CircularWavyProgressIndicator(
                color = if (isInverted()) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary,
                amplitude = 0f,
            )
        } else {
            // Determinate
            CircularWavyProgressIndicator(
                progress = progress,
                color = if (isInverted()) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview
@Composable
private fun CombinedCircularProgressIndicatorPreview() {
    var progress by remember { mutableFloatStateOf(0.5f) }
    var isInverted by remember { mutableStateOf(false) }
    MaterialTheme {
        Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            progress = when (progress) {
                                0f -> 0.15f
                                0.15f -> 0.25f
                                0.25f -> 0.5f
                                0.5f -> 0.75f
                                0.75f -> 0.95f
                                else -> 0f
                            }
                        },
                    ) {
                        Text("Progress")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { isInverted = !isInverted },
                    ) {
                        Text("Invert")
                    }
                }
            },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
            ) {
                CombinedCircularProgressIndicator(progress = { progress }, isInverted = { isInverted })
            }
        }
    }
}
