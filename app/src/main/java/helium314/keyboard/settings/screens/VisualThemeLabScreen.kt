// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.theme.ThemeAsset
import helium314.keyboard.theme.ThemeNormalizedRect
import helium314.keyboard.theme.VisualThemeManager
import helium314.keyboard.theme.VisualThemePreviewGeometry
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualThemeLabScreen(themeId: String, onClickBack: () -> Unit) {
    val context = LocalContext.current
    val theme = remember(themeId) {
        VisualThemeManager.availableThemes(context).firstOrNull { it.id == themeId }
    }
    val previewBitmap = remember(themeId, theme?.manifest?.versionCode) {
        theme?.bitmap(context, ThemeAsset.KEY_PREVIEW)
    }
    val previewImage = remember(previewBitmap) { previewBitmap?.asImageBitmap() }
    val config = theme?.manifest?.keyPreview
    val initialBounds = remember(themeId, previewBitmap, config) {
        if (previewBitmap == null || config == null) null else runCatching {
            VisualThemePreviewGeometry.resolveFaceBounds(
                config,
                previewBitmap.width.toFloat(),
                previewBitmap.height.toFloat(),
            )
        }.getOrNull()
    }
    var bounds by remember(themeId, initialBounds) { mutableStateOf(initialBounds) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom,
        ),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visual_theme_lab)) },
                navigationIcon = { BackButton(onClickBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = theme?.displayName ?: stringResource(R.string.visual_theme_missing),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.visual_theme_lab_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (previewImage == null || bounds == null) {
                Text(stringResource(R.string.visual_theme_lab_no_preview))
                return@Column
            }

            PreviewBoundsEditor(
                image = previewImage,
                bounds = requireNotNull(bounds),
                onBoundsChange = { bounds = it },
            )

            BoundSlider(
                label = stringResource(R.string.visual_theme_bound_left),
                value = requireNotNull(bounds).left,
                onValueChange = { value ->
                    bounds = requireNotNull(bounds).copy(
                        left = value.coerceAtMost(requireNotNull(bounds).right - MIN_FACE_SIZE),
                    )
                },
            )
            BoundSlider(
                label = stringResource(R.string.visual_theme_bound_top),
                value = requireNotNull(bounds).top,
                onValueChange = { value ->
                    bounds = requireNotNull(bounds).copy(
                        top = value.coerceAtMost(requireNotNull(bounds).bottom - MIN_FACE_SIZE),
                    )
                },
            )
            BoundSlider(
                label = stringResource(R.string.visual_theme_bound_right),
                value = requireNotNull(bounds).right,
                onValueChange = { value ->
                    bounds = requireNotNull(bounds).copy(
                        right = value.coerceAtLeast(requireNotNull(bounds).left + MIN_FACE_SIZE),
                    )
                },
            )
            BoundSlider(
                label = stringResource(R.string.visual_theme_bound_bottom),
                value = requireNotNull(bounds).bottom,
                onValueChange = { value ->
                    bounds = requireNotNull(bounds).copy(
                        bottom = value.coerceAtLeast(requireNotNull(bounds).top + MIN_FACE_SIZE),
                    )
                },
            )

            val snippet = boundsSnippet(requireNotNull(bounds))
            Text(
                text = snippet,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.shapes.medium,
                    )
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("huBoard keyPreview", snippet))
                    Toast.makeText(
                        context,
                        R.string.visual_theme_lab_copied,
                        Toast.LENGTH_SHORT,
                    ).show()
                }) {
                    Text(stringResource(R.string.visual_theme_lab_copy))
                }
                TextButton(onClick = { bounds = initialBounds }) {
                    Text(stringResource(R.string.visual_theme_lab_reset))
                }
            }
        }
    }
}

@Composable
private fun PreviewBoundsEditor(
    image: androidx.compose.ui.graphics.ImageBitmap,
    bounds: ThemeNormalizedRect,
    onBoundsChange: (ThemeNormalizedRect) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf(DragHandle.MOVE) }
    val currentBounds by rememberUpdatedState(bounds)
    val currentOnBoundsChange by rememberUpdatedState(onBoundsChange)
    val handleRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }
    val outlineColor = MaterialTheme.colorScheme.primary
    val dimColor = Color.Black.copy(alpha = 0.28f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(Color(0xFF141414), MaterialTheme.shapes.medium)
            .onSizeChanged { canvasSize = it }
            .pointerInput(image, canvasSize) {
                detectDragGestures(
                    onDragStart = { point ->
                        val viewport = previewViewport(canvasSize, image.width, image.height)
                        activeHandle = nearestHandle(
                            point,
                            viewport,
                            currentBounds,
                            handleRadiusPx * 2f,
                        )
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val viewport = previewViewport(canvasSize, image.width, image.height)
                        if (viewport.width <= 0f || viewport.height <= 0f) return@detectDragGestures
                        currentOnBoundsChange(
                            dragBounds(
                                currentBounds,
                                activeHandle,
                                dragAmount.x / viewport.width,
                                dragAmount.y / viewport.height,
                            ),
                        )
                    },
                )
            },
    ) {
        val viewport = previewViewport(canvasSize, image.width, image.height)
        drawImage(
            image = image,
            dstOffset = IntOffset(viewport.left.roundToInt(), viewport.top.roundToInt()),
            dstSize = IntSize(viewport.width.roundToInt(), viewport.height.roundToInt()),
        )
        val face = bounds.toViewport(viewport)
        drawRect(dimColor, Offset(viewport.left, viewport.top), Size(viewport.width, face.top - viewport.top))
        drawRect(dimColor, Offset(viewport.left, face.bottom), Size(viewport.width, viewport.bottom - face.bottom))
        drawRect(dimColor, Offset(viewport.left, face.top), Size(face.left - viewport.left, face.height))
        drawRect(dimColor, Offset(face.right, face.top), Size(viewport.right - face.right, face.height))
        drawRect(outlineColor, Offset(face.left, face.top), Size(face.width, face.height), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        face.corners().forEach { drawCircle(outlineColor, handleRadiusPx, it) }
        drawIntoCanvas { canvas ->
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = min(face.width, face.height) * 0.28f
            }
            val baseline = face.center.y - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.nativeCanvas.drawText("A", face.center.x, baseline, textPaint)
        }
    }
}

@Composable
private fun BoundSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${formatBound(value)}", style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
    }
}

private data class PreviewViewport(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

private data class FaceViewport(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: Offset get() = Offset((left + right) / 2f, (top + bottom) / 2f)
    fun corners() = listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(right, bottom),
        Offset(left, bottom),
    )
}

private enum class DragHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, MOVE }

private fun previewViewport(size: IntSize, imageWidth: Int, imageHeight: Int): PreviewViewport {
    if (size == IntSize.Zero || imageWidth <= 0 || imageHeight <= 0) {
        return PreviewViewport(0f, 0f, 0f, 0f)
    }
    val scale = min(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
    val width = imageWidth * scale
    val height = imageHeight * scale
    return PreviewViewport((size.width - width) / 2f, (size.height - height) / 2f, width, height)
}

private fun ThemeNormalizedRect.toViewport(viewport: PreviewViewport) = FaceViewport(
    left = viewport.left + left * viewport.width,
    top = viewport.top + top * viewport.height,
    right = viewport.left + right * viewport.width,
    bottom = viewport.top + bottom * viewport.height,
)

private fun nearestHandle(
    point: Offset,
    viewport: PreviewViewport,
    bounds: ThemeNormalizedRect,
    threshold: Float,
): DragHandle {
    val corners = bounds.toViewport(viewport).corners()
    val handles = listOf(
        DragHandle.TOP_LEFT,
        DragHandle.TOP_RIGHT,
        DragHandle.BOTTOM_RIGHT,
        DragHandle.BOTTOM_LEFT,
    )
    val closest = handles.indices.minBy { index ->
        hypot(point.x - corners[index].x, point.y - corners[index].y)
    }
    return if (hypot(point.x - corners[closest].x, point.y - corners[closest].y) <= threshold) {
        handles[closest]
    } else {
        DragHandle.MOVE
    }
}

private fun dragBounds(
    bounds: ThemeNormalizedRect,
    handle: DragHandle,
    dx: Float,
    dy: Float,
): ThemeNormalizedRect = when (handle) {
    DragHandle.TOP_LEFT -> bounds.copy(
        left = (bounds.left + dx).coerceIn(0f, bounds.right - MIN_FACE_SIZE),
        top = (bounds.top + dy).coerceIn(0f, bounds.bottom - MIN_FACE_SIZE),
    )
    DragHandle.TOP_RIGHT -> bounds.copy(
        right = (bounds.right + dx).coerceIn(bounds.left + MIN_FACE_SIZE, 1f),
        top = (bounds.top + dy).coerceIn(0f, bounds.bottom - MIN_FACE_SIZE),
    )
    DragHandle.BOTTOM_RIGHT -> bounds.copy(
        right = (bounds.right + dx).coerceIn(bounds.left + MIN_FACE_SIZE, 1f),
        bottom = (bounds.bottom + dy).coerceIn(bounds.top + MIN_FACE_SIZE, 1f),
    )
    DragHandle.BOTTOM_LEFT -> bounds.copy(
        left = (bounds.left + dx).coerceIn(0f, bounds.right - MIN_FACE_SIZE),
        bottom = (bounds.bottom + dy).coerceIn(bounds.top + MIN_FACE_SIZE, 1f),
    )
    DragHandle.MOVE -> {
        val moveX = dx.coerceIn(-bounds.left, 1f - bounds.right)
        val moveY = dy.coerceIn(-bounds.top, 1f - bounds.bottom)
        ThemeNormalizedRect(
            bounds.left + moveX,
            bounds.top + moveY,
            bounds.right + moveX,
            bounds.bottom + moveY,
        )
    }
}

private fun boundsSnippet(bounds: ThemeNormalizedRect): String = """
    "keyPreview": {
      "faceBounds": {
        "left": ${formatBound(bounds.left)},
        "top": ${formatBound(bounds.top)},
        "right": ${formatBound(bounds.right)},
        "bottom": ${formatBound(bounds.bottom)}
      }
    }
""".trimIndent()

private fun formatBound(value: Float): String = String.format(Locale.US, "%.4f", value)

private const val MIN_FACE_SIZE = 0.02f
