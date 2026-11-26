package io.legado.app.inkpaper.design

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.abs

@Composable
fun InkPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = InkTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun InkSubTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = InkTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.ink,
                )
            }
            Text(
                text = title,
                color = colors.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.line)
    }
}

@Composable
fun InkFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.ink else colors.chip,
        label = "chipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.inkOnSolid else colors.inkSecondary,
        label = "chipFg",
    )
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

private val defaultCoverShades = listOf(
    Color(0xFFE7E4DD) to Color(0xFF3A3833),
    Color(0xFF26261F) to Color(0xFFD8D6CF),
    Color(0xFFD9D6CE) to Color(0xFF4A4842),
    Color(0xFFEFEDE7) to Color(0xFF55534C),
    Color(0xFFC9C6BD) to Color(0xFF33312C),
    Color(0xFF3B3B36) to Color(0xFFE0DED7),
)

@Composable
fun InkBookCover(
    coverUrl: String?,
    title: String,
    author: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 6.dp,
    showDot: Boolean = false,
) {
    val colors = InkTheme.colors
    val serifFont = InkTheme.serifFont
    val context = LocalContext.current
    val trimmedUrl = coverUrl?.trim().orEmpty()

    val bitmap by produceState<ImageBitmap?>(initialValue = null, trimmedUrl) {
        value = null
        if (trimmedUrl.isEmpty()) return@produceState
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                value = resource.asImageBitmap()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                value = null
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                value = null
            }
        }
        val requestManager = runCatching { Glide.with(context) }.getOrNull() ?: return@produceState
        runCatching {
            requestManager.asBitmap().load(trimmedUrl).into(target)
        }
        awaitDispose {
            runCatching { requestManager.clear(target) }
        }
    }

    val shadeIndex = remember(title) {
        abs(title.hashCode()) % defaultCoverShades.size
    }
    val (paperShade, inkShade) = defaultCoverShades[shadeIndex]
    val shape = RoundedCornerShape(cornerRadius)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(paperShade)
                .border(0.5.dp, colors.line, shape),
        ) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val verticalTitle = remember(title) {
                    title.take(8).toCharArray().joinToString("\n")
                }
                val verticalAuthor = remember(author) {
                    author.take(6).toCharArray().joinToString("\n")
                }
                Text(
                    text = verticalTitle,
                    color = inkShade,
                    fontFamily = serifFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 9.dp),
                )
                if (verticalAuthor.isNotEmpty()) {
                    Text(
                        text = verticalAuthor,
                        color = inkShade.copy(alpha = 0.55f),
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(colors.bg)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(colors.ink),
            )
        }
    }
}

@Composable
fun InkCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = InkTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(0.5.dp, colors.line),
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Composable
fun InkListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = InkTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) {
                leading()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.ink,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.inkSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.line,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
fun InkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.ink else colors.chip,
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        label = "switchThumb",
    )
    val thumbColor = if (checked) colors.inkOnSolid else colors.surface
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(44.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(
                width = 0.5.dp,
                color = if (checked) colors.ink else colors.line,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
fun InkSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = InkTheme.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.chip)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) colors.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) colors.ink else colors.inkSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun InkEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = InkTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = colors.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = colors.inkSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedButton(
                onClick = onAction,
                shape = CircleShape,
                border = BorderStroke(1.dp, colors.ink),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Text(text = actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
