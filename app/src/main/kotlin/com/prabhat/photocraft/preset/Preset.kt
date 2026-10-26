package com.prabhat.photocraft.preset

import com.prabhat.photocraft.edit.EditState
import com.prabhat.photocraft.edit.M5StateCodec

data class Preset(
    val name: String,
    val state: EditState
) {
    init {
        require(PresetName.isValid(name)) { "Preset name is invalid." }
    }
}

object PresetName {
    const val MAX_LENGTH = 40

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) {
            return null
        }
        if (trimmed.any { it.isISOControl() }) {
            return null
        }
        return trimmed
    }

    fun isValid(name: String): Boolean = normalize(name) == name
}

object PresetCodec {
    private const val SEPARATOR = "|"

    fun encode(preset: Preset): String {
        val nameBytes = preset.name.toByteArray(Charsets.UTF_8)
        val encodedName = buildString(nameBytes.size * 2) {
            for (byte in nameBytes) {
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
        val stateValues = M5StateCodec.encode(preset.state)
        return encodedName + SEPARATOR + stateValues.joinToString(",")
    }

    fun decode(encoded: String?): Preset? {
        if (encoded.isNullOrBlank()) {
            return null
        }
        val separator = encoded.indexOf(SEPARATOR)
        if (separator <= 0 || separator == encoded.lastIndex) {
            return null
        }
        val name = decodeName(encoded.substring(0, separator)) ?: return null
        val values = encoded.substring(separator + 1)
            .split(',')
            .map { it.toIntOrNull() ?: return null }
            .toIntArray()
        val state = M5StateCodec.decode(values) ?: return null
        return Preset(name = name, state = state)
    }

    fun encodeAll(presets: Collection<Preset>): Set<String> =
        presets.mapTo(LinkedHashSet()) { encode(it) }

    fun decodeAll(values: Set<String>?): List<Preset> =
        values.orEmpty()
            .mapNotNull(::decode)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private fun decodeName(hex: String): String? {
        if (hex.isEmpty() || hex.length % 2 != 0) {
            return null
        }
        val bytes = ByteArray(hex.length / 2)
        for (index in bytes.indices) {
            val high = hex[index * 2].digitToIntOrNull(16) ?: return null
            val low = hex[index * 2 + 1].digitToIntOrNull(16) ?: return null
            bytes[index] = ((high shl 4) or low).toByte()
        }
        val name = String(bytes, Charsets.UTF_8)
        return PresetName.normalize(name)?.takeIf { it == name }
    }

    private const val HEX = "0123456789abcdef"
}
