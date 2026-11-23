package com.example.proyecto.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CustomMarkerHelper {

    private const val CURRENT_USER_MARKER_BORDER_COLOR = "#00BCD4"
    private const val OTHER_USER_MARKER_BORDER_COLOR = "#FF9800"

    suspend fun createCustomMarkerBitmap(
        context: Context,
        imageUrl: String?,
        userName: String,
        isCurrentUser: Boolean = false
    ): BitmapDescriptor = withContext(Dispatchers.IO) {
        val size = 140
        val borderSize = 8
        val pointerHeight = 24

        val bitmap = Bitmap.createBitmap(size, size + pointerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val borderColorString = if (isCurrentUser) CURRENT_USER_MARKER_BORDER_COLOR else OTHER_USER_MARKER_BORDER_COLOR
        val borderColor = Color.parseColor(borderColorString)
        paint.color = borderColor
        val center = size / 2f
        val radius = size / 2f

        canvas.drawCircle(center, center, radius, paint)

        val path = Path()
        path.moveTo(center - 20f, size - 10f)
        path.lineTo(center, (size + pointerHeight).toFloat())
        path.lineTo(center + 20f, size - 10f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.WHITE
        val innerRadius = radius - borderSize
        canvas.drawCircle(center, center, innerRadius, paint)

        val finalImageUrl = if (!imageUrl.isNullOrBlank()) imageUrl else com.example.proyecto.data.model.DEFAULT_PROFILE_PICTURE
        val loadedBitmap: Bitmap? = try {
            kotlinx.coroutines.withTimeoutOrNull(3000L) {
                val request = ImageRequest.Builder(context)
                    .data(finalImageUrl)
                    .allowHardware(false)
                    .size((innerRadius * 2).toInt(), (innerRadius * 2).toInt())
                    .scale(coil.size.Scale.FILL)
                    .build()
                val result = ImageLoader(context).execute(request)
                if (result is SuccessResult) {
                    result.drawable.toBitmap(
                        width = (innerRadius * 2).toInt(),
                        height = (innerRadius * 2).toInt()
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }

        if (loadedBitmap != null) {
            val circularBitmap = getCircularBitmap(loadedBitmap)
            canvas.drawBitmap(
                circularBitmap,
                center - innerRadius,
                center - innerRadius,
                null
            )
        } else {
            val initial = userName.take(1).uppercase()
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor
                textSize = 48f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(initial.ifEmpty { "U" }, center, textY, textPaint)
        }

        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawOval(rectF, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }
}
