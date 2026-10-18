package com.devdgames.hollowknightbutmine.ui

import androidx.compose.foundation.background
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

            // Top-Left: PC Controls Quick Helper
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99181C28))
                    .border(1.dp, Color(0x4488AACC), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "⌨️ PC: [A/D] Move  [SPACE] Jump  [Q] Dash  [L-CLICK] Stab  [R-CLICK] Parry  [F] Grab/Headbutt  [E] Heal",
                    color = Color(0xFFD0DCE8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Top-Right: Quick Reset / Respawn Button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x88202432))
                    .border(1.dp, Color(0x66A0C0E0), RoundedCornerShape(8.dp))
                    .clickable { gameViewRef?.world?.reset() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("🔄 Respawn Room", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            label = "◀",
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
            label = "▶",
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
        // Top Row: Heal + Parry + Dash
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Heal / Focus (Hold to heal)
            ControlButton(
                label = "✨",
                subLabel = "HEAL",
                size = 56.dp,
                accentColor = Color(0xFF64B5F6),
                onPressStart = { gameView?.world?.player?.startHeal() },
                onPressEnd = { gameView?.world?.player?.stopHeal() }
            )

            // Parry (Shield with pin clasp)
            ActionButton(
                label = "🛡️",
                subLabel = "PARRY",
                size = 56.dp,
                accentColor = Color(0xFFFFD54F),
                onClick = { gameView?.world?.player?.startParry() }
            )

            // Dash
            ActionButton(
                label = "💨",
                subLabel = "DASH",
                size = 56.dp,
                accentColor = Color(0xFF80DEEA),
                onClick = { gameView?.world?.player?.dash() }
            )
        }

        // Bottom Row: Jump + Stab + Grab / Headbutt
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Jump
            ActionButton(
                label = "🦘",
                subLabel = "JUMP",
                size = 64.dp,
                accentColor = Color(0xFFAED581),
                onClick = { gameView?.world?.player?.jump() }
            )

            // Stab (Safety Pin Thrust)
            ActionButton(
                label = "🧷",
                subLabel = "STAB",
                size = 68.dp,
                accentColor = Color(0xFFE0E0E0),
                onClick = { gameView?.world?.player?.stab() }
            )

            // Grab / Headbutt (Safety Pin Clasp Hook -> Headbutt smash)
            val isHolding = gameView?.world?.player?.state == PlayerState.HOLDING_ENEMY
            ActionButton(
                label = if (isHolding) "💥" else "🪝",
                subLabel = if (isHolding) "HEADBUTT" else "GRAB",
                size = 68.dp,
                accentColor = if (isHolding) Color(0xFFFF7043) else Color(0xFFFFAB91),
                onClick = { gameView?.world?.player?.grab() }
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
            Text(label, fontSize = (size.value * 0.38f).sp)
            Text(
                subLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) accentColor else Color(0xFFCCCCCC)
            )
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
            Text(label, fontSize = (size.value * 0.38f).sp, color = Color.White)
            Text(
                subLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (effectivePressed) accentColor else Color(0xFFAAAAAA)
            )
        }
    }
}
