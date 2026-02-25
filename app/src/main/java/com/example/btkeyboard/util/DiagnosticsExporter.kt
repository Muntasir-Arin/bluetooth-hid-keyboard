package com.example.btkeyboard.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object DiagnosticsExporter {

    fun export(context: Context, entries: List<String>): Uri {
        val dir = File(context.cacheDir, "diagnostics")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "bt_keyboard_diagnostics.txt")
        file.writeText(entries.joinToString(separator = "\n"))
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
