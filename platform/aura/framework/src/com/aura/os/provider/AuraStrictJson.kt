package com.aura.os.provider

import java.math.BigDecimal
import java.nio.charset.StandardCharsets

/**
 * Small, bounded JSON parser used at the OAuth trust boundary.
 * Rejects duplicate keys, noncanonical numbers, malformed escapes and
 * trailing data. Never include sensitive response contents in errors.
 *
 * This is deliberately independent of Android so host security tests can
 * run; compare against a maintained JSON library before production adoption.
 */
internal object AuraStrictJson {
    fun objectValue(json: String, maxBytes: Int = 65_536): Map<String, Any?> {
        require(json.toByteArray(StandardCharsets.UTF_8).size in 1..maxBytes) {
            "JSON response size invalid"
        }
        val reader = Reader(json)
        val parsed = reader.value(0)
        reader.space()
        require(reader.atEnd()) { "Trailing data after JSON" }
        require(parsed is Map<*, *>) { "Expected JSON object" }
        @Suppress("UNCHECKED_CAST")
        return parsed as Map<String, Any?>
    }

    private class Reader(private val data: String) {
        private var offset = 0
        private var elements = 0

        fun atEnd(): Boolean = offset == data.length

        fun space() {
            while (offset < data.length && data[offset] in " \t\n\r") offset++
        }

        fun value(depth: Int): Any? {
            require(depth <= 16) { "Excessive JSON depth" }
            space()
            require(offset < data.length) { "Unexpected end of JSON" }
            return when (data[offset]) {
                '{' -> objectAt(depth + 1)
                '[' -> arrayAt(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> number()
            }
        }

        private fun <T> literal(word: String, result: T): T {
            require(data.startsWith(word, offset)) { "Invalid JSON literal" }
            offset += word.length
            return result
        }

        private fun bump() {
            elements++
            require(elements <= 1_024) { "Too many JSON elements" }
        }

        private fun objectAt(depth: Int): Map<String, Any?> {
            offset++
            space()
            val values = linkedMapOf<String, Any?>()
            if (accept('}')) return values
            while (true) {
                require(offset < data.length && data[offset] == '"') {
                    "Expected object key"
                }
                val key = string()
                require(key.isNotEmpty() && key.length <= 256) { "Invalid JSON key" }
                space()
                expect(':')
                require(!values.containsKey(key)) { "Duplicate JSON key" }
                bump()
                values[key] = value(depth)
                space()
                if (accept('}')) return values
                expect(',')
                space()
            }
        }

        private fun arrayAt(depth: Int): List<Any?> {
            offset++
            space()
            val values = mutableListOf<Any?>()
            if (accept(']')) return values
            while (true) {
                bump()
                values.add(value(depth))
                space()
                if (accept(']')) return values
                expect(',')
            }
        }

        private fun expect(char: Char) {
            require(accept(char)) { "Unexpected JSON character" }
        }

        private fun accept(char: Char): Boolean {
            space()
            if (offset >= data.length || data[offset] != char) return false
            offset++
            return true
        }

        private fun string(): String {
            expect('"')
            val output = StringBuilder()
            while (true) {
                require(offset < data.length) { "Unterminated JSON string" }
                val c = data[offset++]
                if (c == '"') break
                if (c == '\\') {
                    require(offset < data.length) { "Incomplete JSON escape" }
                    val escaped = data[offset++]
                    output.append(when (escaped) {
                        '"' -> '"'
                        '\\' -> '\\'
                        '/' -> '/'
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            require(offset + 4 <= data.length) { "Incomplete unicode escape" }
                            val hex = data.substring(offset, offset + 4)
                            require(hex.all { it in "0123456789abcdefABCDEF" }) {
                                "Invalid unicode escape"
                            }
                            offset += 4
                            hex.toInt(16).toChar()
                        }
                        else -> error("Invalid JSON escape")
                    })
                } else {
                    require(c >= ' ') { "Control character in JSON string" }
                    output.append(c)
                }
                require(output.length <= 32_768) { "JSON string too long" }
            }
            val result = output.toString()
            for (i in result.indices) {
                val c = result[i]
                if (Character.isHighSurrogate(c)) {
                    require(i + 1 < result.length &&
                        Character.isLowSurrogate(result[i + 1])) {
                        "Invalid unicode surrogate pair"
                    }
                } else if (Character.isLowSurrogate(c)) {
                    require(i > 0 && Character.isHighSurrogate(result[i - 1])) {
                        "Invalid unicode surrogate pair"
                    }
                }
            }
            return result
        }

        private fun number(): Any {
            val start = offset
            if (data[offset] == '-') offset++
            require(offset < data.length) { "Invalid JSON number" }
            if (data[offset] == '0') {
                offset++
                require(offset >= data.length || data[offset] !in '0'..'9') {
                    "Leading zero in JSON number"
                }
            } else {
                require(data[offset] in '1'..'9') { "Invalid JSON number" }
                while (offset < data.length && data[offset] in '0'..'9') offset++
            }
            var decimal = false
            if (offset < data.length && data[offset] == '.') {
                decimal = true
                offset++
                val begin = offset
                while (offset < data.length && data[offset] in '0'..'9') offset++
                require(offset > begin) { "Invalid decimal number" }
            }
            if (offset < data.length && data[offset] in "eE") {
                decimal = true
                offset++
                if (offset < data.length && data[offset] in "+-") offset++
                val begin = offset
                while (offset < data.length && data[offset] in '0'..'9') offset++
                require(offset > begin) { "Invalid exponent number" }
            }
            val value = data.substring(start, offset)
            require(value.length <= 100) { "JSON number too long" }
            return if (decimal) BigDecimal(value) else
                value.toLongOrNull() ?: error("JSON integer outside supported range")
        }
    }
}
