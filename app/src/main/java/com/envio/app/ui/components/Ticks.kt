package com.envio.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.envio.app.ui.envioColors

/**
 * Estado de un mensaje propio, dibujado a mano (no usa iconos de nadie):
 * reloj = pendiente, una palomita = enviado, dos grises = entregado, dos azules = leido, circulo rojo = error.
 */
@Composable
fun MessageTicks(status: String, modifier: Modifier = Modifier) {
    val c = envioColors
    val description = when (status) {
        "PENDING" -> "Pendiente"
        "SENT" -> "Enviado"
        "DELIVERED" -> "Entregado"
        "READ" -> "Leido"
        else -> "Error de envio"
    }
    val color = when (status) {
        "READ" -> c.tickRead
        "FAILED" -> c.danger
        else -> c.tickMuted
    }
    Canvas(
        modifier
            .size(width = 18.dp, height = 12.dp)
            .semantics { contentDescription = description }
    ) {
        val sw = 1.5.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun check(dx: Float) {
            val p = Path().apply {
                moveTo(dx + size.width * 0.10f, size.height * 0.55f)
                lineTo(dx + size.width * 0.28f, size.height * 0.85f)
                lineTo(dx + size.width * 0.62f, size.height * 0.15f)
            }
            drawPath(p, color, style = stroke)
        }

        when (status) {
            "PENDING" -> {
                val r = size.height * 0.42f
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                drawCircle(color, r, Offset(cx, cy), style = stroke)
                drawLine(color, Offset(cx, cy), Offset(cx, cy - r * 0.6f), sw, StrokeCap.Round)
                drawLine(color, Offset(cx, cy), Offset(cx + r * 0.5f, cy), sw, StrokeCap.Round)
            }
            "SENT" -> check(size.width * 0.15f)
            "DELIVERED", "READ" -> {
                check(0f)
                check(size.width * 0.28f)
            }
            else -> {
                val r = size.height * 0.45f
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                drawCircle(color, r, Offset(cx, cy), style = stroke)
                drawLine(color, Offset(cx, cy - r * 0.5f), Offset(cx, cy + r * 0.1f), sw, StrokeCap.Round)
                drawCircle(color, sw * 0.6f, Offset(cx, cy + r * 0.45f))
            }
        }
    }
}
