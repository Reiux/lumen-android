package com.reiux.lumen.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GroupPosition { Single, Top, Middle, Bottom }

fun groupPositionOf(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.Top
    index == count - 1 -> GroupPosition.Bottom
    else -> GroupPosition.Middle
}

fun groupedCardShape(
    position: GroupPosition,
    outer: Dp = 24.dp,
    inner: Dp = 4.dp,
): RoundedCornerShape = when (position) {
    GroupPosition.Single -> RoundedCornerShape(outer)
    GroupPosition.Top -> RoundedCornerShape(
        topStart = outer,
        topEnd = outer,
        bottomStart = inner,
        bottomEnd = inner,
    )
    GroupPosition.Middle -> RoundedCornerShape(inner)
    GroupPosition.Bottom -> RoundedCornerShape(
        topStart = inner,
        topEnd = inner,
        bottomStart = outer,
        bottomEnd = outer,
    )
}

@Composable
fun animatedGroupedCardShape(
    position: GroupPosition,
    pressed: Boolean,
    outer: Dp = 24.dp,
    inner: Dp = 4.dp,
    pressedCorner: Dp = 14.dp,
): RoundedCornerShape {
    val targetTop = when (position) {
        GroupPosition.Single, GroupPosition.Top -> if (pressed) pressedCorner else outer
        GroupPosition.Middle, GroupPosition.Bottom -> if (pressed) pressedCorner else inner
    }
    val targetBottom = when (position) {
        GroupPosition.Single, GroupPosition.Bottom -> if (pressed) pressedCorner else outer
        GroupPosition.Top, GroupPosition.Middle -> if (pressed) pressedCorner else inner
    }
    val motionSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val topCorner by animateDpAsState(
        targetValue = targetTop,
        animationSpec = motionSpec,
        label = "groupedCardTopCorner"
    )
    val bottomCorner by animateDpAsState(
        targetValue = targetBottom,
        animationSpec = motionSpec,
        label = "groupedCardBottomCorner"
    )
    return RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )
}
