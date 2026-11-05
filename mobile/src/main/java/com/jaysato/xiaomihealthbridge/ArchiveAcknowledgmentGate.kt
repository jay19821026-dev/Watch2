package com.jaysato.xiaomihealthbridge

import kotlinx.coroutines.ensureActive

object ArchiveAcknowledgmentGate {
    suspend fun storeThenAcknowledge(store: suspend () -> Unit, acknowledge: suspend () -> Unit) {
        store()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        acknowledge()
    }
}
