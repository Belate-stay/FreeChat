package com.freechat.util

import java.io.File
import java.io.IOException
import java.io.InputStream

/** Never buffer an arbitrary picker stream in the heap before checking its size. */
object AttachmentImport {
    class TooLarge : IOException()

    fun copyWithLimit(input: InputStream, target: File, limit: Int, checkActive: () -> Unit = {}): Long {
        require(limit > 0)
        var count = 0L
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    checkActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > limit) throw TooLarge()
                    output.write(buffer, 0, read)
                }
            }
            return count
        } catch (error: Exception) {
            target.delete() // Only the new internal copy passed by the caller; never the original URI.
            throw error
        }
    }
}
