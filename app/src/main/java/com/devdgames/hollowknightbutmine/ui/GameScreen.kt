package com.devdgames.hollowknightbutmine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.devdgames.hollowknightbutmine.game.GameSurfaceView
import com.devdgames.hollowknightbutmine.game.PlayerState
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    onGameViewCreated: ((GameSurfaceView) -> Unit)? = null
) {
    var gameViewRef by remember { mutableStateOf<GameSurfaceView?>(null) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0C12))) {
        // 1. Android SurfaceView Game Rendering
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                GameSurfaceView(context).also {
                    gameViewRef = it
                    onGameViewCreated?.invoke(it)
                }
            }
        )

        // 2. Touch Controls Overlay (Virtual D-pad + Combat Action Buttons)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
        ) {
            // Left Side: D-Pad / Movement Controls
            VirtualDPad(
                modifier = Modifier.align(Alignment.BottomStart),
                onDirectionChanged = { dirX ->
                    gameViewRef?.inputX = dirX
                }
            )

            // Top-Right: Map + Respawn
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xAA243B48))
                        .border(1.dp, Color(0x8897D8D8), RoundedCornerShape(8.dp))
                        .clickable { gameViewRef?.world?.interact() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("✧ Interact", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x88202432))
                        .border(1.dp, Color(0x66A0C0E0), RoundedCornerShape(8.dp))
                        .clickable { gameViewRef?.world?.toggleMap() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("🗺 Map", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x88202432))
                        .border(1.dp, Color(0x66A0C0E0), RoundedCornerShape(8.dp))
                        .clickable { gameViewRef?.world?.reset() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("🔄 Return to Bench", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Right Side: Combat & Movement Action Buttons
            CombatActionButtons(
                modifier = Modifier.align(Alignment.BottomEnd),
                gameView = gameViewRef
            )
        }
    }
}

@Composable
fun VirtualDPad(
    modifier: Modifier = Modifier,
    onDirectionChanged: (Float) -> Unit
) {
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    val updateInput = {
        val dir = when {
            leftPressed && !rightPressed -> -1f
            rightPressed && !leftPressed -> 1f
            else -> 0f
        }
        onDirectionChanged(dir)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Button
        ControlButton(
            label = "left",
            subLabel = "LEFT",
            size = 68.dp,
            isPressed = leftPressed,
            onPressStart = {
                leftPressed = true
                updateInput()
            },
            onPressEnd = {
                leftPressed = false
                updateInput()
            }
        )

        // Right Button
        ControlButton(
            label = "right",
            subLabel = "RIGHT",
            size = 68.dp,
            isPressed = rightPressed,
            onPressStart = {
                rightPressed = true
                updateInput()
            },
            onPressEnd = {
                rightPressed = false
                updateInput()
            }
        )
    }
}

@Composable
fun CombatActionButtons(
    modifier: Modifier = Modifier,
    gameView: GameSurfaceView?
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Row: Heal + Dash
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Heal / Focus (Hold to heal)
            ControlButton(
                label = "heal",
                subLabel = "HEAL",
                size = 56.dp,
                accentColor = Color(0xFF64B5F6),
                onPressStart = { gameView?.world?.startHealOrInteract() },
                onPressEnd = { gameView?.world?.player?.stopHeal() }
            )

            // Dash
            ActionButton(
                label = "dash",
                subLabel = "DASH",
                size = 56.dp,
                accentColor = Color(0xFF80DEEA),
                onClick = { gameView?.world?.player?.dash() }
            )
        }

        // Bottom Row: Jump + Light attack (slash) + Stab
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Jump
            ActionButton(
                label = "jump",
                subLabel = "JUMP",
                size = 64.dp,
                accentColor = Color(0xFFAED581),
                onClick = { gameView?.world?.player?.jump() }
            )

            // Light attack: the quick sword slash. This is the primary attack button.
            ActionButton(
                label = "slash",
                subLabel = "SLASH",
                size = 68.dp,
                accentColor = Color(0xFFE0E0E0),
                onClick = { gameView?.world?.player?.slash() }
            )

            // Stab: the committed forward thrust with the point.
            ActionButton(
                label = "stab",
                subLabel = "STAB",
                size = 64.dp,
                accentColor = Color(0xFFFFD54F),
                onClick = { gameView?.world?.player?.stab() }
            )
        }
    }
}

@Composable
fun ActionButton(
    label: String,
    subLabel: String,
    size: androidx.compose.ui.unit.Dp,
    accentColor: Color = Color.White,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (isPressed) listOf(accentColor.copy(alpha = 0.5f), Color(0x992B3045))
                    else listOf(Color(0x992B3045), Color(0x99181C28))
                )
            )
            .border(
                width = if (isPressed) 2.5.dp else 1.5.dp,
                color = if (isPressed) accentColor else accentColor.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onClick()
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ControlGlyph(
                name = label,
                tint = if (isPressed) accentColor else Color(0xFFEAF1F7),
                modifier = Modifier.size(size * 0.43f)
            )
            // Icon-only controls: keep the button face clean and readable.
        }
    }
}

@Composable
fun ControlButton(
    label: String,
    subLabel: String,
    size: androidx.compose.ui.unit.Dp,
    accentColor: Color = Color(0xFFB0BEC5),
    isPressed: Boolean = false,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    var pressedState by remember { mutableStateOf(false) }
    val effectivePressed = isPressed || pressedState

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (effectivePressed) listOf(accentColor.copy(alpha = 0.5f), Color(0x992B3045))
                    else listOf(Color(0x992B3045), Color(0x99181C28))
                )
            )
            .border(
                width = if (effectivePressed) 2.5.dp else 1.5.dp,
                color = if (effectivePressed) accentColor else accentColor.copy(alpha = 0.45f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressedState = true
                        onPressStart()
                        tryAwaitRelease()
                        pressedState = false
                        onPressEnd()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ControlGlyph(
                name = label,
                tint = if (effectivePressed) accentColor else Color(0xFFEAF1F7),
                modifier = Modifier.size(size * 0.43f)
            )
            // Icon-only controls: keep the button face clean and readable.
        }
    }
}

@Composable
private fun ControlGlyph(name: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = Stroke(width = size.minDimension * 0.105f, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = s.width) {
            drawLine(tint, androidx.compose.ui.geometry.Offset(x1, y1), androidx.compose.ui.geometry.Offset(x2, y2), width, cap = StrokeCap.Round)
        }
        fun path(points: List<Pair<Float, Float>>, close: Boolean = false, fill: Boolean = false) {
            val p = Path()
            points.forEachIndexed { i, point ->
                val px = point.first * w
                val py = point.second * h
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            if (close) p.close()
            drawPath(p, tint, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else s)
        }
        when (name) {
            "heal" -> {
                // Medical cross and two small glints.
                drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(w * .39f, h * .18f), size = androidx.compose.ui.geometry.Size(w * .22f, h * .64f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f))
                drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(w * .18f, h * .39f), size = androidx.compose.ui.geometry.Size(w * .64f, h * .22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .05f))
                line(w * .82f, h * .12f, w * .82f, h * .28f, w * .07f)
                line(w * .74f, h * .20f, w * .90f, h * .20f, w * .07f)
            }
            "slash" -> {
                // A sword held diagonally plus the arc it sweeps.
                line(w * .24f, h * .76f, w * .74f, h * .24f, w * .11f)
                line(w * .31f, h * .60f, w * .47f, h * .77f, w * .09f)
                line(w * .14f, h * .86f, w * .25f, h * .75f, w * .09f)
                drawArc(
                    color = tint,
                    startAngle = 205f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * .30f, h * .16f),
                    size = androidx.compose.ui.geometry.Size(w * .58f, h * .58f),
                    style = s
                )
            }
            "stab" -> {
                // The same sword held level with the point forward.
                line(w * .26f, h * .48f, w * .88f, h * .48f, w * .11f)
                line(w * .32f, h * .32f, w * .32f, h * .64f, w * .09f)
                line(w * .12f, h * .48f, w * .27f, h * .48f, w * .09f)
                line(w * .56f, h * .74f, w * .86f, h * .74f, w * .06f)
            }
            "dash" -> {
                line(w * .08f, h * .30f, w * .56f, h * .30f, w * .09f)
                line(w * .02f, h * .50f, w * .52f, h * .50f, w * .09f)
                line(w * .12f, h * .70f, w * .56f, h * .70f, w * .09f)
                path(listOf(.50f to .16f, .88f to .50f, .50f to .84f), close = false)
                line(w * .48f, h * .50f, w * .88f, h * .50f, w * .09f)
            }
            "jump" -> {
                path(listOf(.20f to .55f, .50f to .20f, .80f to .55f))
                line(w * .50f, h * .22f, w * .50f, h * .78f, w * .11f)
                line(w * .22f, h * .82f, w * .78f, h * .82f, w * .08f)
            }

            "left" -> path(listOf(.68f to .15f, .28f to .50f, .68f to .85f), close = false)
            "right" -> path(listOf(.32f to .15f, .72f to .50f, .32f to .85f), close = false)
            else -> drawCircle(tint, radius = w * .25f)
        }
    }
}
