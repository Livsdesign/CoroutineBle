package com.livsdesign.coroutineble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

fun Int.getIntBytes(size: Int = 4, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray {
    val buffer = ByteBuffer.allocate(4).order(order)
    buffer.putInt(this)
    val bytes = buffer.array()
    return if (order == ByteOrder.LITTLE_ENDIAN) {
        bytes.copyOfRange(0, size)
    } else {
        bytes.copyOfRange(4 - size, 4)
    }
}

fun Byte.toUInt8(): Int {
    return this.toInt().and(0xff)
}

fun toUInt16(high: Byte, low: Byte): Int {
    return (high.toUInt8() shl 8).or(low.toUInt8())
}

fun ByteArray.toHexString(char: Char? = null): String {
    if (isEmpty()) return "Empty Byte Array"
    val sb = StringBuilder()
    for (i in indices) {
        val tmp = this[i].toUInt8()
        sb.append(String.format("%02X", tmp))
        if (char != null) {
            sb.append(char)
        }
    }
    return sb.toString()
}

fun String.toUUID(): UUID {
    return UUID.fromString(this)
}

fun Int.intToBytes(): ByteArray {
    val b = ByteBuffer.allocate(4)
    b.putInt(this)
    return b.array()
}

//"aa:bb:cc:dd:ee:ff"->bytes=new byte[]{0xaa,0xbb,0xcc,0xdd,0xee,0xff}
fun String.macToByteArray(): ByteArray {
    return try {
        val splitResult = this.split(":")
        val temp = ArrayList<Byte>()
        for (s in splitResult) {
            temp.add(s.toInt(16).toByte())
        }
        temp.toByteArray()
    } catch (e: Exception) {
        byteArrayOf()
    }
}

fun ByteArray.merge(bytes: ByteArray): ByteArray {
    val newArray = ByteArray(this.size + bytes.size)
    System.arraycopy(this, 0, newArray, 0, size)
    System.arraycopy(bytes, 0, newArray, size, bytes.size)
    return newArray
}

fun Byte.merge(bytes: ByteArray): ByteArray {
    val newArray = ByteArray(1 + bytes.size)
    newArray[0] = this
    System.arraycopy(bytes, 0, newArray, 1, bytes.size)
    return newArray
}

fun ByteArray.merge(byte: Byte): ByteArray {
    val newArray = ByteArray(this.size + 1)
    System.arraycopy(this, 0, newArray, 0, size)
    newArray[newArray.lastIndex] = byte
    return newArray
}

fun Byte.merge(byte: Byte): ByteArray {
    val newArray = ByteArray(2)
    newArray[0] = this
    newArray[1] = byte
    return newArray
}