package com.prabhat.photocraft.export

import android.content.Intent

object ExportPicker {
    fun createDocumentIntent(suggestedFileName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = ExportSettings.JPEG_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, suggestedFileName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}
