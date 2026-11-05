package com.jaysato.xiaomihealthbridge

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ExternalAiEntry {
    fun open(context: Context, gemini: Boolean, watch: Boolean = false) {
        val name = if (gemini) "Gemini" else "ChatGPT"
        AlertDialog.Builder(context)
            .setTitle("詢問 $name")
            .setMessage("將開啟外部 AI。此入口不會自動傳送健康數值或波形；你輸入的內容由該服務處理，可能需要登入或付費。外部回答不是醫療診斷。")
            .setNegativeButton("取消", null)
            .setPositiveButton("開啟") { _, _ ->
                try {
                    val assistant = if (gemini && watch) context.packageManager.getLaunchIntentForPackage("com.google.android.wearable.assistant") else null
                    context.startActivity(assistant ?: Intent(Intent.ACTION_VIEW, Uri.parse(if (gemini) "https://gemini.google.com/" else "https://chatgpt.com/")))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "無可用 App／瀏覽器，請在手機開啟 $name。", Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, "系統不允許開啟；請從 App 清單啟動 $name。", Toast.LENGTH_LONG).show()
                }
            }.show()
    }
}

