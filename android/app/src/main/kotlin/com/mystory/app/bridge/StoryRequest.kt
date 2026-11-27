package com.mystory.app.bridge

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.mystory.app.domain.model.AspectRatio
import com.mystory.app.domain.model.FrameRatePreset
import com.mystory.app.domain.model.MediaType
import com.mystory.app.domain.model.OutputSettings
import com.mystory.app.domain.model.ResolutionTier

/** One photo or video picked in Flutter, identified by its `MediaStore` row id. */
data class PickedAsset(val id: Long, val mediaType: MediaType) {

    fun contentUri(): Uri {
        val collection = when (mediaType) {
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(collection, id)
    }
}

/** Parsing helpers translating the Dart-side wire format into engine domain types. */
object StoryRequest {

    fun parseAssets(raw: List<Map<String, Any?>>): List<PickedAsset> = raw.map { entry ->
        val id = entry["id"]?.toString()?.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid asset id: ${entry["id"]}")
        val isVideo = entry["isVideo"] as? Boolean
            ?: throw IllegalArgumentException("Missing isVideo flag for asset $id")
        PickedAsset(id, if (isVideo) MediaType.VIDEO else MediaType.PHOTO)
    }

    fun parseSettings(aspectRatio: String?, fps: Int?, resolution: String?): OutputSettings {
        val ratio = when (aspectRatio) {
            "16:9" -> AspectRatio.RATIO_16_9
            "9:16" -> AspectRatio.RATIO_9_16
            "1:1" -> AspectRatio.RATIO_1_1
            else -> throw IllegalArgumentException("Unsupported aspect ratio: $aspectRatio")
        }
        val frameRate = FrameRatePreset.entries.firstOrNull { it.fps == fps }
            ?: throw IllegalArgumentException("Unsupported frame rate: $fps")
        val tier = when (resolution ?: "1080p") {
            "720p" -> ResolutionTier.R720P
            "1080p" -> ResolutionTier.R1080P
            "2k" -> ResolutionTier.R2K
            "4k" -> ResolutionTier.R4K
            else -> throw IllegalArgumentException("Unsupported resolution: $resolution")
        }
        return OutputSettings(tier, ratio, frameRate)
    }

    /** Extracts the numeric `MediaStore` id that photo_manager uses as an asset id. */
    fun assetIdFromUri(uri: String): String? =
        uri.substringBefore('?').trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
}
