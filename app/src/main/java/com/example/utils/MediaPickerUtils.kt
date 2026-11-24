package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val MAX_IMAGE_DIMENSION = 1600
private const val MAX_INLINE_MEDIA_BYTES = 18 * 1024 * 1024 // 18 MB safe limit under 20 MB

suspend fun loadScaledBitmapFromUri(context: Context, uri: Uri): Result<Bitmap> =
    withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            var sampleSize = 1
            while (width / sampleSize > MAX_IMAGE_DIMENSION || height / sampleSize > MAX_IMAGE_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Unable to decode selected image."))
            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

data class VideoPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val displayName: String
)

suspend fun loadVideoPayloadFromUri(context: Context, uri: Uri): Result<VideoPayload> =
    withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
            val name = queryDisplayName(context, uri) ?: "selected_video.mp4"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Unable to read selected video."))

            if (bytes.size > MAX_INLINE_MEDIA_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("Video exceeds 18 MB inline limit. Please pick a shorter clip.")
                )
            }
            Result.success(VideoPayload(bytes, mimeType, name))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    } catch (_: Exception) {
        null
    }
}

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, titlePrefix: String = "SmartAI"): Result<String> =
    withContext(Dispatchers.IO) {
        try {
            val fileName = "${titlePrefix}_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SmartAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Failed to create gallery entry."))

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success("Saved to Pictures/SmartAI")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

suspend fun shareBitmap(context: Context, bitmap: Bitmap): Result<Unit> {
    return try {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(dir, "smart_ai_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Smart AI Image"))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
