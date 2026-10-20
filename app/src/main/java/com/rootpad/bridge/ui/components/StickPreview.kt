package com.rootpad.bridge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * 右スティックの動作プレビュー。x/y は [-1, 1]。
 */
@Composable
fun StickPreview(
    x: Float,
    y: Float,
    deadzone: Float,
    modifier: Modifier = Modifier
) {
    val line = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val dzColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.92f

        // 外円
        drawCircle(line.copy(alpha = 0.5f), radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.03f))
        // デッドゾーン
        if (deadzone > 0f) {
            drawCircle(dzColor.copy(alpha = 0.18f), radius = r * deadzone * 2f, center = Offset(cx, cy))
        }
        // 十字線
        drawLine(line.copy(alpha = 0.5f), Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
        drawLine(line.copy(alpha = 0.5f), Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = r * 0.02f, cap = StrokeCap.Round)

        // 現在のスティック位置 (y は下が正 → 画面では上を負方向に描画)
        val px = cx + (x.coerceIn(-1f, 1f)) * r
        val py = cy + (y.coerceIn(-1f, 1f)) * r
        val magnitude = kotlin.math.hypot(x, y)
        val dotR = r * 0.12f
        drawCircle(
            accent.copy(alpha = 0.25f),
            radius = dotR * 2.2f,
            center = Offset(px, py)
        )
        drawCircle(accent, radius = dotR, center = Offset(px, py))
        // 中心から点まで線
        drawLine(
            accent.copy(alpha = 0.5f),
            Offset(cx, cy),
            Offset(px, py),
            strokeWidth = r * 0.03f,
            cap = StrokeCap.Round
        )
        // 振幅バー (右下に数値表示用の枠は UI 側)
        val barW = size.width * 0.96f
        val barH = size.height * 0.06f
        drawRect(
            line.copy(alpha = 0.3f),
            topLeft = Offset(size.width * 0.02f, size.height - barH),
            size = Size(barW, barH)
        )
        drawRect(
            accent,
            topLeft = Offset(size.width * 0.02f, size.height - barH),
            size = Size(barW * magnitude.coerceIn(0f, 1f), barH)
        )
    }
}
