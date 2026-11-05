package com.jaysato.xiaomihealthbridge

import android.content.Context
import com.google.android.gms.wearable.Wearable

internal object WearCommandDispatcher {
    sealed interface Outcome {
        data object Sent : Outcome
        data class NoUniqueWatch(val connectedNodeCount: Int) : Outcome
        data class Failed(val message: String) : Outcome
    }

    fun send(context: Context, path: String, onOutcome: (Outcome) -> Unit) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                val node = WearCommandTargetPolicy.uniqueTarget(nodes)
                if (node == null) {
                    onOutcome(Outcome.NoUniqueWatch(nodes.size))
                    return@addOnSuccessListener
                }
                Wearable.getMessageClient(context).sendMessage(node.id, path, byteArrayOf())
                    .addOnSuccessListener { onOutcome(Outcome.Sent) }
                    .addOnFailureListener { error ->
                        onOutcome(Outcome.Failed(error.localizedMessage ?: "訊息傳送失敗"))
                    }
            }
            .addOnFailureListener { error ->
                onOutcome(Outcome.Failed(error.localizedMessage ?: "無法搜尋已連線手錶"))
            }
    }

    fun feedback(
        outcome: Outcome,
        sentMessage: String = "ECG 啟動命令已送達手錶；請在手錶畫面雙指接觸錶冠與側鍵。送達不代表量測已完成。"
    ): String = when (outcome) {
        Outcome.Sent -> sentMessage
        is Outcome.NoUniqueWatch -> WearCommandTargetPolicy.failureMessage(outcome.connectedNodeCount)
        is Outcome.Failed -> "手錶命令失敗：${outcome.message}"
    }
}
