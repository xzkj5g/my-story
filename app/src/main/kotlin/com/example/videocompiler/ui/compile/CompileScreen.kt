package com.example.videocompiler.ui.compile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.videocompiler.domain.model.CompileJobStatus
import com.example.videocompiler.ui.MediaCompilerUiState

@Suppress("FunctionNaming")
@Composable
fun CompileScreen(
    state: MediaCompilerUiState,
    onStartCompile: () -> Unit,
    onCancelCompile: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Compile")
            Text(
                "Default output: ${state.outputSettings.resolutionTier.name.removePrefix("R")} • " +
                    "${state.outputSettings.aspectRatio.name.removePrefix("RATIO_").replace('_', ':')} • " +
                    "${state.outputSettings.frameRate.fps}fps",
            )
            val job = state.compileJob
            Text(
                text = buildStatusText(job),
                modifier = Modifier.testTag("statusText"),
            )
            state.compileWarning?.let { warning ->
                Text(
                    text = warning,
                    modifier = Modifier.testTag("compileWarningText"),
                )
            }
            Button(
                onClick = onStartCompile,
                enabled = job?.status != CompileJobStatus.RUNNING,
                modifier = Modifier.testTag("compileButton"),
            ) {
                Text("Start compilation")
            }
            if (job?.status == CompileJobStatus.RUNNING) {
                OutlinedButton(
                    onClick = onCancelCompile,
                    modifier = Modifier.testTag("cancelCompileButton"),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun buildStatusText(job: com.example.videocompiler.domain.model.CompileJob?): String = when {
    job == null -> "Idle"
    job.status == CompileJobStatus.RUNNING -> "Running: ${job.progressPercent}%"
    job.status == CompileJobStatus.SUCCEEDED -> "Succeeded"
    job.status == CompileJobStatus.CANCELLED -> "Cancelled"
    else -> "Failed: ${job.failureReason.orEmpty()}"
}
