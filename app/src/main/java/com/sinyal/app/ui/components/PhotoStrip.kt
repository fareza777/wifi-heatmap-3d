package com.sinyal.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sinyal.app.data.PlacePhoto
import com.sinyal.app.ui.theme.Accent
import com.sinyal.app.ui.theme.Ink
import com.sinyal.app.ui.theme.TextTone

private const val THUMB_TARGET_PX = 256

/**
 * The landmarks photographed during the walk, as a scrollable strip.
 *
 * Tapping a 3D marker would need ray-picking against the scene; a thumbnail row
 * gets to the same place with less machinery and is easier to hit on a phone.
 * Each thumbnail carries the same accent as its pin in the model, so the two
 * read as the same object.
 */
@Composable
fun PhotoStrip(
    photos: List<PlacePhoto>,
    pathFor: (PlacePhoto) -> String,
    onSelect: (PlacePhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoCard(
                photo = photo,
                path = pathFor(photo),
                onClick = { onSelect(photo) },
            )
        }
    }
}

@Composable
private fun PhotoCard(
    photo: PlacePhoto,
    path: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(shape)
                .background(Ink.SurfaceHigh)
                .border(1.dp, Accent.Base.copy(alpha = 0.5f), shape),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = rememberThumbnail(path)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = photo.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextTone.Tertiary,
                )
            }
        }
        Text(
            text = photo.label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextTone.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * Decodes at roughly thumbnail size rather than full resolution.
 *
 * A dozen 1280 px JPEGs decoded whole would cost tens of megabytes for images
 * displayed at 96 dp.
 */
@Composable
fun rememberThumbnail(path: String): ImageBitmap? = remember(path) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)

        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }
                .first { sample -> longest / sample <= THUMB_TARGET_PX || sample >= 16 }
        }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    }.getOrNull()
}

/** Full-size decode, for the detail sheet. */
@Composable
fun rememberFullImage(path: String): ImageBitmap? = remember(path) {
    runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
}
