internal object RawSnapshotSampleRatePersistencePolicy {
    enum class Action {
        WRITE,
        CLEAR,
        KEEP
    }

    fun action(hasSampleRateField: Boolean, newMeasurementSession: Boolean): Action = when {
        hasSampleRateField -> Action.WRITE
        newMeasurementSession -> Action.CLEAR
        else -> Action.KEEP
    }
}
