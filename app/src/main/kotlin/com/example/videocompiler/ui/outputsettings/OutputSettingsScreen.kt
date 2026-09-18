@file:Suppress("FunctionNaming")

package com.example.videocompiler.ui.outputsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.videocompiler.domain.model.AspectRatio
import com.example.videocompiler.domain.model.FrameRatePreset
import com.example.videocompiler.domain.model.OutputSettings
import com.example.videocompiler.domain.model.ResolutionTier
import com.example.videocompiler.ui.MediaCompilerController
import com.example.videocompiler.ui.MediaCompilerUiState

/**
 * Preset picker for [OutputSettings.resolutionTier], [OutputSettings.aspectRatio], and
 * [OutputSettings.frameRate] (FR-005): only the defined preset enum values are selectable —
 * there is no free-form numeric width/height/fps entry point anywhere in this screen.
 */
@Composable
fun OutputSettingsScreen(
    state: MediaCompilerUiState,
    controller: MediaCompilerController,
) {
    val settings = state.outputSettings
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Output settings")
            Text("${settings.outputWidth}x${settings.outputHeight} @ ${settings.frameRate.fps}fps")

            Text("Resolution")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResolutionTier.entries.forEach { tier ->
                    FilterChip(
                        selected = settings.resolutionTier == tier,
                        onClick = { controller.setOutputSettings(settings.copy(resolutionTier = tier)) },
                        label = { Text(tier.name) },
                        modifier = Modifier.testTag("resolution-${tier.name}"),
                    )
                }
            }

            Text("Aspect ratio")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatio.entries.forEach { ratio ->
                    FilterChip(
                        selected = settings.aspectRatio == ratio,
                        onClick = { controller.setOutputSettings(settings.copy(aspectRatio = ratio)) },
                        label = { Text(ratio.name) },
                        modifier = Modifier.testTag("aspectRatio-${ratio.name}"),
                    )
                }
            }

            Text("Frame rate")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrameRatePreset.entries.forEach { fps ->
                    FilterChip(
                        selected = settings.frameRate == fps,
                        onClick = { controller.setOutputSettings(settings.copy(frameRate = fps)) },
                        label = { Text("${fps.fps}") },
                        modifier = Modifier.testTag("frameRate-${fps.fps}"),
                    )
                }
            }
        }
    }
}
