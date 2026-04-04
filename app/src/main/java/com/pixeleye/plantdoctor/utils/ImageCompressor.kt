package com.pixeleye.plantdoctor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

private const val MAX_DIMENSION = 1280
private const val JPEG_QUALITY = 50

/**
 * Decodes an image from [uri], downscales so the largest dimension is capped
 * at [MAX_DIMENSION] pixels (preserving aspect ratio), and returns the Bitmap.
 *
 * Uses inSampleSize for memory-efficient decoding — the full-resolution bitmap
 * is never fully loaded into RAM.
 */
fun decodeDownscaledBitmap(context: Context, uri: Uri): Bitmap {
    // Step 1: Read dimensions only (no pixel allocation)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }

    val width = options.outWidth
    val height = options.outHeight
    if (width <= 0 || height <= 0) {
        throw IllegalArgumentException("Cannot decode image dimensions from URI")
    }

    // Step 2: Calculate inSampleSize (power of 2)
    var inSampleSize = 1
    val maxDim = maxOf(width, height)
    while (maxDim / (inSampleSize * 2) >= MAX_DIMENSION) {
        inSampleSize *= 2
    }

    // Step 3: Decode at reduced resolution
    val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: throw IllegalStateException("Failed to decode image from URI")

    // Step 4: Fine-scale if still above the cap after inSampleSize
    if (decoded.width > MAX_DIMENSION || decoded.height > MAX_DIMENSION) {
        val ratio = decoded.width.toFloat() / decoded.height.toFloat()
        val (newW, newH) = if (decoded.width >= decoded.height) {
            MAX_DIMENSION to (MAX_DIMENSION / ratio).toInt()
        } else {
            (MAX_DIMENSION * ratio).toInt() to MAX_DIMENSION
        }
        return Bitmap.createScaledBitmap(decoded, newW, newH, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    return decoded
}

/**
 * Decodes from [uri] using [decodeDownscaledBitmap], then compresses to JPEG.
 */
fun compressImageHighQuality(context: Context, uri: Uri): ByteArray {
    val bitmap = decodeDownscaledBitmap(context, uri)
    val stream = ByteArrayOutputStream()
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, JPEG_QUALITY, stream)
    } else {
        @Suppress("DEPRECATION")
        bitmap.compress(Bitmap.CompressFormat.WEBP, JPEG_QUALITY, stream)
    }
    bitmap.recycle()
    return stream.toByteArray()
}
