@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.model

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * Swift ergonomics: KotlinByteArray is awkward to consume from Swift,
 * NSData bridges straight to Data. Exported as
 * `DocumentImagesNSDataKt.frontData(images)` / `backData(images)`:
 *
 * ```swift
 * if let front = DocumentImagesNSDataKt.frontData(images) {
 *     let image = UIImage(data: front)
 * }
 * ```
 *
 * Both COPY the bytes, so a later [DocumentImages.dispose] does not
 * zero what Swift is holding — the copy's lifecycle (and §7 hygiene)
 * is then the client's.
 */
public fun frontData(images: DocumentImages): NSData? = images.front?.toNSData()

public fun backData(images: DocumentImages): NSData = images.back.toNSData()

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
