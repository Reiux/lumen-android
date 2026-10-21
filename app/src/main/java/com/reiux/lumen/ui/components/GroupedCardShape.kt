package com.reiux.lumen.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
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
    outer: Dp = 20.dp,
    inner: Dp = 6.dp,
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
