package com.jaysato.xiaomihealthbridge

object BiomedicalRawDetailText {
    fun spo2Preview(points: Int): String =
        "保留的原始波形預覽 ${points.coerceAtLeast(0)} 點；不以 70–100 的數值範圍猜測 SpO₂ 百分比。"
}
