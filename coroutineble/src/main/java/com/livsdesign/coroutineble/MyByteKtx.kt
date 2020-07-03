package com.livsdesign.coroutineble

import java.nio.ByteBuffer
import java.util.*

fun Byte.unsignedByteToInt(): Int {
    return this.toInt().and(0xff)
}

fun unsignedByteToInt(byte1: Byte, byte2: Byte): Int {
    return (byte1.unsignedByteToInt() shl 8).or(byte2.unsignedByteToInt())
}

fun ByteArray.toHexString(): String {
    if (isEmpty()) return "Empty Byte Array"
    val sb = StringBuilder()
    for (i in indices) {
        val tmp = this[i].unsignedByteToInt()
        sb.append(String.format("%02X", tmp))
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