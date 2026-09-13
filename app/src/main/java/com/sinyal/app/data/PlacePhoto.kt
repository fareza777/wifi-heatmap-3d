package com.sinyal.app.data

/**
 * A photo taken during the walk, pinned to where the phone stood.
 *
 * The point is not the picture itself but the anchor it provides: a heatmap is
 * abstract until one of its cells is labelled "meja PC", after which the whole
 * model becomes readable as a real home rather than coloured blocks.
 *
 * [label] starts as an auto-generated placeholder and is meant to be edited
 * afterwards — asking for a name mid-walk would interrupt the scan for the sake
 * of something better done later with the photo in view.
 */
data class PlacePhoto(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val fileName: String,
    val capturedAtMs: Long,
)
