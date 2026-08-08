package com.sevis.photos

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.posix.memcpy

/** Reads the raw bytes at a local "file://…" URI — iOS's counterpart to Android's
 *  contentResolver.openInputStream(uri).use { it.readBytes() }, used by uploadImage/
 *  uploadVideo on files MediaPicker copied into the app's tmp directory. */
@OptIn(ExperimentalForeignApi::class)
fun readBytesAtPath(uriString: String): ByteArray? {
    val url = NSURL(string = uriString) ?: return null
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
    return bytes
}

/** Writes [bytes] to a plain filesystem [path] (not a "file://…" URI) — used by
 *  VideoPlayer.kt to cache a downloaded video before local playback. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun writeBytesToFile(bytes: ByteArray, path: String): Boolean {
    if (bytes.isEmpty()) return NSData().writeToFile(path, atomically = true)
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    return data.writeToFile(path, atomically = true)
}
