package com.example.videocompiler.ui

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

class DeviceResourceMonitor(
    private val context: Context,
) {

    fun warningMessage(): String? {
        val warnings = buildList {
            if (isStorageLow()) {
                add("storage is running low")
            }
            if (isMemoryLow()) {
                add("memory is running low")
            }
        }
        return if (warnings.isEmpty()) {
            null
        } else {
            "Warning: ${warnings.joinToString(" and ")}. Compilation may fail, but no hard clip limit is enforced."
        }
    }

    private fun isStorageLow(): Boolean {
        val statFs = StatFs(context.filesDir.absolutePath)
        return statFs.availableBytes < LOW_STORAGE_THRESHOLD_BYTES
    }

    private fun isMemoryLow(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.lowMemory
    }

    private companion object {
        const val LOW_STORAGE_THRESHOLD_BYTES = 512L * 1024L * 1024L
    }
}
