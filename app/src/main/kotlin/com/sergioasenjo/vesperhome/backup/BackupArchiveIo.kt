package com.sergioasenjo.vesperhome.backup

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class CopyBudget(private val maximum: Long) {
    private var copied = 0L

    fun add(byteCount: Int) {
        copied += byteCount
        require(copied <= maximum) { "Backup data is too large" }
    }
}

internal fun ZipOutputStream.writeEntry(name: String, input: InputStream, timestamp: Long) {
    putNextEntry(ZipEntry(name).apply { time = timestamp })
    input.copyTo(this)
    closeEntry()
}

internal fun copyLimited(input: InputStream, output: OutputStream, maximum: Long, budget: CopyBudget) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        copied += count
        require(copied <= maximum) { "Backup entry is too large" }
        budget.add(count)
        output.write(buffer, 0, count)
    }
}
