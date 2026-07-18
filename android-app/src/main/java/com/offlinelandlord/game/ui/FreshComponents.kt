package com.offlinelandlord.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinelandlord.game.R
import com.offlinelandlord.game.ui.theme.Cream
import com.offlinelandlord.game.ui.theme.Ink
import com.offlinelandlord.game.ui.theme.Lavender
import com.offlinelandlord.game.ui.theme.LavenderDeep
import com.offlinelandlord.game.ui.theme.Mint
import com.offlinelandlord.game.ui.theme.MintDeep
import com.offlinelandlord.game.ui.theme.Peach
import com.offlinelandlord.game.ui.theme.PeachDeep

@Composable
fun FreshScenicBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.fresh_countryside_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.06f),
                    0.52f to Color.White.copy(alpha = 0.10f),
                    1f to Cream.copy(alpha = 0.30f),
                ),
            )
        }
        content()
    }
}

@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xEFFFFFFF),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = LavenderDeep.copy(alpha = 0.14f))
            .clip(RoundedCornerShape(28.dp))
            .background(tint)
            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(28.dp)),
        content = content,
    )
}

@Composable
fun FreshButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = PeachDeep,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.75f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun FreshOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = LavenderDeep,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.5.dp, color.copy(alpha = if (enabled) 0.65f else 0.25f)),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PastelAvatar(
    name: String,
    isBot: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Lavender,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent, Mint)))
            .border(3.dp, Color.White.copy(alpha = 0.92f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE0CF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isBot) "AI" else name.trim().take(1).ifBlank { "友" },
                color = if (isBot) MintDeep else Ink,
                fontWeight = FontWeight.Black,
                fontSize = if (isBot) 18.sp else 22.sp,
            )
        }
    }
}
