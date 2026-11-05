// ARCHIVO GENERADO por replica/design/gen_compose.py. No editar a mano: cambia los tokens y vuelve a generar.
package com.envio.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class EnvioPalette(
    val bg: Color,
    val surface: Color,
    val border: Color,
    val borderInput: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val success: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val chatBg: Color,
    val bubbleIn: Color,
    val bubbleOut: Color,
    val tickRead: Color,
    val tickMuted: Color,
    val avatarText: Color,
    val avatar1: Color,
    val avatar2: Color,
    val avatar3: Color,
    val avatar4: Color,
    val avatar5: Color,
    val avatar6: Color,
    val avatar7: Color,
    val avatar8: Color
)

val LightPalette = EnvioPalette(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF4F5F7),
    border = Color(0xFFE3E6EA),
    borderInput = Color(0xFF868E98),
    text = Color(0xFF14171C),
    textMuted = Color(0xFF5B6470),
    accent = Color(0xFF2F5BEA),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFC62F2F),
    success = Color(0xFF1F7A4D),
    dangerContainer = Color(0xFFFDE7E7),
    onDangerContainer = Color(0xFF5C1111),
    chatBg = Color(0xFFEEF0F4),
    bubbleIn = Color(0xFFFFFFFF),
    bubbleOut = Color(0xFFDCE4FC),
    tickRead = Color(0xFF1F4FD6),
    tickMuted = Color(0xFF5F6975),
    avatarText = Color(0xFFFFFFFF),
    avatar1 = Color(0xFF4F5FB8),
    avatar2 = Color(0xFF2D7A66),
    avatar3 = Color(0xFFA8435B),
    avatar4 = Color(0xFF7E4FA8),
    avatar5 = Color(0xFF9A5B22),
    avatar6 = Color(0xFF2F7396),
    avatar7 = Color(0xFF6E7020),
    avatar8 = Color(0xFFB0472E)
)

val DarkPalette = EnvioPalette(
    bg = Color(0xFF0F1216),
    surface = Color(0xFF171B21),
    border = Color(0xFF2A3038),
    borderInput = Color(0xFF7D8794),
    text = Color(0xFFE8EAED),
    textMuted = Color(0xFFA3ABB6),
    accent = Color(0xFF9DB2FF),
    onAccent = Color(0xFF0B1330),
    danger = Color(0xFFFF9E94),
    success = Color(0xFF74D6A4),
    dangerContainer = Color(0xFF5C1F1F),
    onDangerContainer = Color(0xFFFFDAD6),
    chatBg = Color(0xFF0B0E12),
    bubbleIn = Color(0xFF1D2229),
    bubbleOut = Color(0xFF25326B),
    tickRead = Color(0xFF9DB2FF),
    tickMuted = Color(0xFF9AA3AE),
    avatarText = Color(0xFFFFFFFF),
    avatar1 = Color(0xFF4F5FB8),
    avatar2 = Color(0xFF2D7A66),
    avatar3 = Color(0xFFA8435B),
    avatar4 = Color(0xFF7E4FA8),
    avatar5 = Color(0xFF9A5B22),
    avatar6 = Color(0xFF2F7396),
    avatar7 = Color(0xFF6E7020),
    avatar8 = Color(0xFFB0472E)
)

object Dim {
    val topBar = 56.dp
    val tabRow = 48.dp
    val chatRow = 72.dp
    val avatarList = 48.dp
    val avatarHeader = 40.dp
    val avatarLarge = 120.dp
    val fab = 56.dp
    val inputMin = 48.dp
    val touchMin = 48.dp
    val bubbleRadius = 12.dp
    val bubbleTail = 4.dp
    const val bubbleMaxPercent = 0.8f
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val pill = 999.dp
}

object Space {
    val s0 = 0.dp
    val s1 = 2.dp
    val s2 = 4.dp
    val s3 = 8.dp
    val s4 = 12.dp
    val s5 = 16.dp
    val s6 = 24.dp
    val s7 = 32.dp
    val s8 = 48.dp
}

object Txt {
    val meta = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight(400))
    val xs = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(500))
    val sm = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(400))
    val base = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight(400))
    val lg = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight(600))
    val xl = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight(700))
}
