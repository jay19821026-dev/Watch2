package com.jaysato.xiaomihealthbridge

internal class RawPersistenceOutcome {
    private var latestOperation = 0L
    @Volatile var failure: String? = null
        private set

    @Synchronized fun begin(): Long = ++latestOperation

    @Synchronized fun complete(operation: Long, error: String?) {
        if (operation == latestOperation) failure = error
    }
}
