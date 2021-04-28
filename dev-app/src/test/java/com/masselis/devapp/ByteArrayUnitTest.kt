package com.masselis.devapp

import com.masselis.rxbluetoothkotlin.internal.toHexString
import junit.framework.Assert.assertEquals
import org.junit.Test

class ByteArrayUnitTest {

    @Test
    fun testToString() {
        println()
        println("-------- testToString")

        val exceptedValue = "56A8FF34B2"

        val byteArray = arrayOf(0x56.toByte(), 0xA8.toByte(), 0xFF.toByte(), 0x34.toByte(), 0xB2.toByte()).toByteArray()

        println("exceptedValue = $exceptedValue")
        println("byteArray = ${byteArray.toHexString()}")

        assertEquals(exceptedValue, byteArray.toHexString())
    }
}