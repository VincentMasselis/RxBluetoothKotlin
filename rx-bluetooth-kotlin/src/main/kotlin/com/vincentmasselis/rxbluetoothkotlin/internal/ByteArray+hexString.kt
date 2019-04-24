package com.vincentmasselis.rxbluetoothkotlin.internal

//Copied from https://bitbucket.org/snippets/gelin/zLebo/extension-functions-to-format-bytes-as-hex

private val CHARS = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

/**
 *  Returns the string of two characters representing the HEX value of the byte.
 */
internal fun Byte.toHexString(): String {
    val i = this.toInt()
    val char2 = CHARS[i and 0x0f]
    val char1 = CHARS[i shr 4 and 0x0f]
    return "$char1$char2"
}

/**
 *  Returns the HEX representation of ByteArray data.
 */
fun ByteArray.toHexString(): String {
    val builder = StringBuilder()
    for (b in this) {
        builder.append(b.toHexString())
    }
    return builder.toString()
}