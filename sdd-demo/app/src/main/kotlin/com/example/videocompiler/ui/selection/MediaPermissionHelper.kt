package com.example.videocompiler.ui.selection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime media-permission decisions for the selection flow (research.md §9). Only relevant to
 * the fallback picker path: the system Photo Picker (`ActivityResultContracts.PickVisualMedia`,
 * Android 13+) requires no runtime permission at all, so callers should prefer it when
 * [isPhotoPickerAvailable] is true and only fall back to requesting [requiredPermissions] (and a
 * classic storage-access picker) on older OS versions.
 */
object MediaPermissionHelper {

    /**
     * The system Photo Picker is available (and permission-free) starting with Android 13
     * (API 33), backported via Google Play services on some older devices, but this app treats
     * API 33+ as the reliable floor per research.md §9.
     */
    fun isPhotoPickerAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Runtime permissions to request when [isPhotoPickerAvailable] is false, i.e. the app must
     * fall back to a classic storage-backed picker/query flow.
     */
    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        else ->
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Whether every permission in [requiredPermissions] is already granted. */
    fun hasRequiredPermissions(context: Context): Boolean =
        isPhotoPickerAvailable() || requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
}
