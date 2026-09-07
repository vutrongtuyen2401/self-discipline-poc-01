package com.example.selfdisciplinepoc01.ui.design.animation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Các Animation Primitives tái sử dụng trong Cultivation UI Design System.
 *
 * NGUYÊN TẮC BẢN LỀ:
 * Animation thuần túy là lớp hiển thị (Presentation Layer).
 * Toàn bộ trạng thái nghiệp vụ (Business State) phải được xác định độc lập và trước khi animation diễn ra.
 * Animation tuyệt đối KHÔNG can thiệp vào logic task completion, unlock, points hay persistence.
 */
object CultivationAnimation {

    const val DURATION_FAST_MS = 150
    const val DURATION_NORMAL_MS = 300
    const val DURATION_SLOW_MS = 600
    const val DURATION_BREATHE_MS = 1800

    /**
     * Hoạt ảnh thay đổi giá trị tiến trình mượt mà (Smooth Progress Transition).
     */
    @Composable
    fun animateProgress(targetProgress: Float): State<Float> {
        return animateFloatAsState(
            targetValue = targetProgress.coerceIn(0f, 1f),
            animationSpec = tween(
                durationMillis = DURATION_SLOW_MS,
                easing = FastOutSlowInEasing
            ),
            label = "CultivationProgressAnimation"
        )
    }

    /**
     * Hoạt ảnh chuyển đổi màu sắc viền/hào quang khi chọn (Selection Border Glow).
     */
    @Composable
    fun animateSelectionColor(
        isSelected: Boolean,
        selectedColor: Color,
        unselectedColor: Color
    ): State<Color> {
        return animateColorAsState(
            targetValue = if (isSelected) selectedColor else unselectedColor,
            animationSpec = tween(
                durationMillis = DURATION_FAST_MS,
                easing = LinearOutSlowInEasing
            ),
            label = "CultivationSelectionColor"
        )
    }

    /**
     * Hoạt ảnh phát sáng thở nhẹ (Subtle Breathing Glow) dành cho card nhiệm vụ đang thực hiện.
     */
    @Composable
    fun rememberBreathingAlpha(): State<Float> {
        val infiniteTransition = rememberInfiniteTransition(label = "BreathingGlowTransition")
        return infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = DURATION_BREATHE_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "BreathingGlowAlpha"
        )
    }
}

/**
 * Modifier tạo hiệu ứng nảy nhẹ khi nhấn (Press Feedback Scale Effect).
 * Khi người dùng chạm vào button hoặc card, item thu nhỏ nhẹ về 0.96f và nảy lại khi thả tay.
 */
fun Modifier.pressScaleEffect(
    interactionSource: MutableInteractionSource? = null,
    targetScale: Float = 0.96f
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 500f
        ),
        label = "PressScaleEffect"
    )

    this.scale(scale)
}

/**
 * Modifier tạo viền hào quang thở nhẹ theo chu kỳ linh lực.
 */
fun Modifier.subtleBreathingBorder(
    baseColor: Color,
    shape: Shape,
    strokeWidth: Dp = 1.dp
): Modifier = composed {
    val alpha by CultivationAnimation.rememberBreathingAlpha()
    this.border(
        width = strokeWidth,
        color = baseColor.copy(alpha = alpha),
        shape = shape
    )
}
