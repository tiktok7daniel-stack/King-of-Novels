package app.riwaya.core

import java.io.File
import java.security.MessageDigest

/** Writes the whole text or nothing: a crash mid-write never leaves half a file. */
fun File.writeAtomic(text: String) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        delete()
        tmp.renameTo(this)
    }
}

fun File.readOrNull(): String? = runCatching { if (exists()) readText() else null }.getOrNull()

fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
