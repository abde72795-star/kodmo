/*
 * Adapted from Kotori (https://github.com/tailolicon/kotori), Apache-2.0.
 */
package eu.kanade.presentation.theme.kotori

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object KotoriShapes {
    val cover = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 4.dp,
    )

    val row = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 6.dp,
    )

    val button = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 8.dp,
        bottomEnd = 20.dp,
        bottomStart = 20.dp,
    )
}
