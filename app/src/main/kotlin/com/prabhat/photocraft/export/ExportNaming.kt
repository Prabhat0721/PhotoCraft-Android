package com.prabhat.photocraft.export

object ExportNaming {
    private val INVALID_FILE_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    fun suggestedFileName(displayName: String?): String {
        val leaf = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            .orEmpty()
        val rawBase = leaf.substringBeforeLast('.', leaf)
        var base = buildString {
            for (character in rawBase) {
                if (character in INVALID_FILE_CHARS || character.isISOControl()) {
                    append('_')
                } else {
                    append(character)
                }
            }
        }.trim().trim('.').trim()

        if (base.length > ExportSettings.MAX_BASE_NAME_LENGTH) {
            base = base.take(ExportSettings.MAX_BASE_NAME_LENGTH)
            if (base.lastOrNull()?.isHighSurrogate() == true) {
                base = base.dropLast(1)
            }
            base = base.trimEnd().trimEnd('.')
        }

        if (base.isBlank()) {
            base = ExportSettings.FALLBACK_BASE_NAME
        }

        return "$base.${ExportSettings.FILE_EXTENSION}"
    }
}
