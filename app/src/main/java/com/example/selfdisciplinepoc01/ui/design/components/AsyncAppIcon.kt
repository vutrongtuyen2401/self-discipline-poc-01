package com.example.selfdisciplinepoc01.ui.design.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bộ nhớ đệm LRU toàn cục in-memory cho Application Icon Bitmaps (tối đa 100 app icons).
 * Tránh đọc đĩa hoặc decode lặp lại trên Main Thread.
 */
object AppIconMemoryCache {
    private const val MAX_CACHE_SIZE = 100
    private val cache = LruCache<String, ImageBitmap>(MAX_CACHE_SIZE)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}

/**
 * Component hiển thị Icon thật của ứng dụng Android một cách an toàn và tối ưu hiệu năng.
 * - Tự động tải bất đồng bộ qua [Dispatchers.IO].
 * - Lưu cache in-memory để lướt Grid mượt mà 60/120fps.
 * - Hiển thị chữ cái linh phù (Rune Fallback) khi đang tải hoặc không tìm thấy icon.
 */
@Composable
fun AsyncAppIcon(
    packageName: String,
    fallbackAppName: String = "",
    modifier: Modifier = Modifier,
    size: Dp = CultivationTheme.spacing.iconLg,
    grayscale: Boolean = false
) {
    val context = LocalContext.current
    val cachedBitmap = AppIconMemoryCache.get(packageName)

    val iconState = produceState<ImageBitmap?>(initialValue = cachedBitmap, key1 = packageName) {
        if (cachedBitmap != null) {
            value = cachedBitmap
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            loadAppIconBitmap(context, packageName)
        }
        if (loaded != null) {
            AppIconMemoryCache.put(packageName, loaded)
            value = loaded
        }
    }

    val shape = CultivationTheme.shapes.avatar
    val currentIcon = iconState.value

    if (currentIcon != null) {
        Image(
            bitmap = currentIcon,
            contentDescription = fallbackAppName.ifEmpty { packageName },
            modifier = modifier
                .size(size)
                .clip(shape),
            colorFilter = if (grayscale) {
                androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
                )
            } else null
        )
    } else {
        // Fallback Avatar dạng Phù Văn / Ký tự ngọc giản
        val initialLetter = fallbackAppName.trim().firstOrNull()?.uppercase()
            ?: packageName.split(".").lastOrNull()?.firstOrNull()?.uppercase()
            ?: "仙"

        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(CultivationTheme.colors.cardBackgroundElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialLetter,
                style = CultivationTheme.typography.titleCard.copy(
                    fontSize = (size.value * 0.45f).sp,
                    fontWeight = FontWeight.Bold,
                    color = CultivationTheme.colors.spiritTeal
                )
            )
        }
    }
}

private fun loadAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val drawable = appInfo.loadIcon(pm)
        drawableToBitmap(drawable)?.asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}
