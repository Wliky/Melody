package com.wliky.melody.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * 二维码渲染：ZXing 生成矩阵 → Compose Canvas 逐格绘制。
 * 白底黑码保证扫码器识别率。
 */
@Composable
fun QrImage(
    content: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color(0xFF111111),
    lightColor: Color = Color.White,
) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            1,
            1,
            mapOf(EncodeHintType.MARGIN to 0),
        )
    }
    Canvas(modifier = modifier) {
        val cell = size.width / matrix.width
        drawRect(color = lightColor)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                if (matrix[x, y]) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
