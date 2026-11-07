package com.jaysato.xiaomihealthbridge

import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class HealthSummaryTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val dashboard = WearHealthStore.dashboard(this)
        val root = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("AILISA HEALTH BRIDGE", 10f, 0xFF64D2FF.toInt()))
            .addContent(spacer(12f))
            .addContent(text(if (dashboard.heartRate > 0) "${dashboard.heartRate} bpm" else "— bpm", 30f, 0xFFFF6478.toInt()))
            .addContent(spacer(10f))
            .addContent(text("步數  %,d".format(dashboard.steps), 18f, 0xFFF5F5F7.toInt()))
            .addContent(text(if (dashboard.oxygen > 0) "血氧  ${dashboard.oxygen.toInt()}%" else "血氧  —", 17f, 0xFF64DFA8.toInt()))
            .addContent(spacer(12f))
            .addContent(text(if (dashboard.syncOk) "✓ 已同步" else "! 點 App 重試", 14f, 0xFFB8B8C0.toInt()))
            .build()
        val layout = LayoutElementBuilders.Layout.Builder().setRoot(root).build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(layout).build()).build()
        return Futures.immediateFuture(TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(60_000)
            .setTileTimeline(timeline).build())
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())

    private fun text(value: String, size: Float, color: Int) = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
            .setSize(DimensionBuilders.sp(size)).setColor(ColorBuilders.argb(color)).build())
        .setModifiers(ModifiersBuilders.Modifiers.Builder().build())
        .build()

    private fun spacer(height: Float) = LayoutElementBuilders.Spacer.Builder()
        .setHeight(DimensionBuilders.dp(height)).build()
}
