package com.jaysato.xiaomihealthbridge

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class HealthComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val dashboard = WearHealthStore.dashboard(this)
        val short = if (dashboard.heartRate > 0) "${dashboard.heartRate} bpm" else "— bpm"
        val long = "$short · %,d 步".format(dashboard.steps)
        return if (request.complicationType == ComplicationType.LONG_TEXT) longData(long) else shortData(short)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData =
        if (type == ComplicationType.LONG_TEXT) longData("72 bpm · 8,000 步") else shortData("72 bpm")

    private fun shortData(value: String) = ShortTextComplicationData.Builder(
        PlainComplicationText.Builder(value).build(),
        PlainComplicationText.Builder("Health Bridge $value").build()
    ).setTitle(PlainComplicationText.Builder("心率").build()).build()

    private fun longData(value: String) = LongTextComplicationData.Builder(
        PlainComplicationText.Builder(value).build(),
        PlainComplicationText.Builder("Health Bridge $value").build()
    ).setTitle(PlainComplicationText.Builder("健康摘要").build()).build()
}
