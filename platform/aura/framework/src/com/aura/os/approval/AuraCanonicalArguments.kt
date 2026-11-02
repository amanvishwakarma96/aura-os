package com.aura.os.approval

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Deterministic binary encoding of a narrow, already validated action argument map.
 * Version marker, sorted keys, typed values, and length prefixes prevent ambiguity.
 * No floats, maps, arrays, nulls or arbitrary objects are supported.
 */
object AuraCanonicalArguments {
    fun encode(arguments: Map<String, Any?>): ByteArray {
        require(arguments.size <= 64) { "Too many argument keys" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1) // encoding version
            out.writeInt(arguments.size)
            for ((key, value) in arguments.toSortedMap()) {
                require(key.isNotEmpty() && key.length <= 128)
                writeUtf8(out, key)
                when (value) {
                    is String -> {
                        out.writeByte(1)
                        writeUtf8(out, value)
                    }
                    is Boolean -> {
                        out.writeByte(2)
                        out.writeByte(if (value) 1 else 0)
                    }
                    is Int -> {
                        out.writeByte(3)
                        out.writeInt(value)
                    }
                    else -> throw IllegalArgumentException("Unsupported argument type")
                }
            }
        }
        return bytes.toByteArray().also { require(it.size <= 16_384) }
    }

    private fun writeUtf8(out: DataOutputStream, text: String) {
        val data = text.toByteArray(Charsets.UTF_8)
        require(data.size <= 4096) { "Encoded text exceeds limit" }
        out.writeInt(data.size)
        out.write(data)
    }
}
