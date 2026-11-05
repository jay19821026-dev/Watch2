package com.jaysato.xiaomihealthbridge

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal object RawSnapshotCodec {
    private const val MAGIC = 0x52535701
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 4096
    private const val MAX_TEXT_BYTES = 1024 * 1024

    fun encode(values: Map<String, *>): ByteArray {
        require(values.size <= MAX_ENTRIES)
        val bytes = object : ByteArrayOutputStream() {
            override fun write(value: Int) {
                require(count < MAX_BYTES)
                super.write(value)
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                require(length >= 0 && count <= MAX_BYTES - length)
                super.write(buffer, offset, length)
            }
        }
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(values.size)
            values.toSortedMap().forEach { (key, value) ->
                output.writeText(key)
                when (value) {
                    is String -> { output.writeByte(1); output.writeText(value) }
                    is Int -> { output.writeByte(2); output.writeInt(value) }
                    is Long -> { output.writeByte(3); output.writeLong(value) }
                    is Float -> { output.writeByte(4); output.writeInt(value.toRawBits()) }
                    is Boolean -> { output.writeByte(5); output.writeByte(if (value) 1 else 0) }
                    is Set<*> -> {
                        require(value.size <= MAX_ENTRIES && value.all { it is String })
                        output.writeByte(6)
                        output.writeInt(value.size)
                        value.map { it as String }.sorted().forEach { output.writeText(it) }
                    }
                    else -> error("Unsupported raw snapshot type.")
                }
                require(bytes.size() <= MAX_BYTES)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Map<String, Any> {
        require(bytes.size in 8..MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            val count = input.readInt().also { require(it in 0..MAX_ENTRIES) }
            val values = linkedMapOf<String, Any>()
            repeat(count) {
                val key = input.readText()
                require(key !in values)
                values[key] = when (input.readUnsignedByte()) {
                    1 -> input.readText()
                    2 -> input.readInt()
                    3 -> input.readLong()
                    4 -> Float.fromBits(input.readInt())
                    5 -> input.readUnsignedByte().also { require(it in 0..1) } == 1
                    6 -> {
                        val size = input.readInt().also { require(it in 0..MAX_ENTRIES) }
                        val members = linkedSetOf<String>()
                        repeat(size) { require(members.add(input.readText())) }
                        members
                    }
                    else -> error("Unsupported raw snapshot type tag.")
                }
            }
            require(input.read() == -1)
            values
        }
    }

    private fun DataOutputStream.writeText(text: String) {
        require(text.length <= MAX_TEXT_BYTES)
        val encoded = Charsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(text))
        require(encoded.remaining() <= MAX_TEXT_BYTES)
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String {
        val size = readInt().also { require(it in 0..MAX_TEXT_BYTES && it <= available()) }
        val bytes = ByteArray(size)
        readFully(bytes)
        return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }
}
