package com.rootpad.bridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChipKind { OK, WARN, FAIL, INFO, IDLE }

@Composable
fun StatusChip(text: String, kind: ChipKind) {
    val (bg, fg) = when (kind) {
        ChipKind.OK -> Color(0xFF1B5E34) to Color(0xFFB9F6CA)
        ChipKind.WARN -> Color(0xFF6D4C00) to Color(0xFFFFE082)
        ChipKind.FAIL -> Color(0xFF7F1D1D) to Color(0xFFFFCDD2)
        ChipKind.INFO -> Color(0xFF1E3A5F) to Color(0xFFBBDEFB)
        ChipKind.IDLE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            key,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        Text(
            value,
            fontSize = 13.sp,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HintText(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )
}
