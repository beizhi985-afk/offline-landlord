package com.offlinelandlord.game.uno.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.offlinelandlord.game.R

/** UNO 专用横屏背景；不影响斗地主使用的公共风景背景。 */
@Composable
fun UnoBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.uno_bg_lakeside),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color(0x140B3140),
                    0.52f to Color.Transparent,
                    1f to Color(0x1A08363A),
                ),
            )
        }
        content()
    }
}
