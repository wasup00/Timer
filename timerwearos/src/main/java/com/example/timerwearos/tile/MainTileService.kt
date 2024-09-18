package com.example.timerwearos.tile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.LayoutRootPreview
import com.google.android.horologist.compose.tools.buildDeviceParameters
import com.google.android.horologist.tiles.SuspendingTileService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

private const val RESOURCES_VERSION = "1"

@OptIn(ExperimentalHorologistApi::class)
class MainTileService : SuspendingTileService() {

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest
    ): TileBuilders.Tile {
        val countdownText = getCountdownText()
        val singleTileTimeline = TimelineBuilders.Timeline.Builder().addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder().setLayout(
                LayoutElementBuilders.Layout.Builder().setRoot(tileLayout(this, countdownText))
                    .build()
            ).build()
        ).build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(singleTileTimeline)
            .setFreshnessIntervalMillis(1000) // Update every second
            .build()
    }

    private suspend fun getCountdownText(): String = suspendCancellableCoroutine { continuation ->
        val countdownRef = FirebaseDatabase.getInstance().reference.child("selectedDate")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dateStr = snapshot.getValue(String::class.java)
                if (dateStr != null) {
                    val countdownText = calculateCountdown(dateStr)
                    continuation.resume(countdownText)
                } else {
                    continuation.resume("No timer set")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                continuation.resume("Error: ${error.message}")
            }
        }
        countdownRef.addListenerForSingleValueEvent(listener)

        continuation.invokeOnCancellation {
            countdownRef.removeEventListener(listener)
        }
    }

    private fun calculateCountdown(dateStr: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val targetDate = LocalDateTime.parse(dateStr, formatter)
        val now = LocalDateTime.now()
        val remainingTime = java.time.Duration.between(now, targetDate)

        if (remainingTime.isNegative) {
            return "Countdown finished!"
        }

        val days = remainingTime.toDays()
        val hours = remainingTime.toHours() % 24
        val minutes = remainingTime.toMinutes() % 60
        val seconds = remainingTime.seconds % 60

        return "${days}d ${hours}h ${minutes}m ${seconds}s"
    }
}

private fun tileLayout(
    context: Context,
    countdownText: String
): LayoutElementBuilders.LayoutElement {
    return PrimaryLayout.Builder(buildDeviceParameters(context.resources)).setResponsiveContentInsetEnabled(true)
        .setContent(
            Text.Builder(context, countdownText)
                .setColor(argb(Colors.DEFAULT.onSurface))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .build()
        ).build()
}

@Preview(
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true,
    backgroundColor = 0xff000000,
    showBackground = true
)
@Composable
fun TilePreview() {
    LayoutRootPreview(root = tileLayout(LocalContext.current, "2d 5h 30m 15s"))
}