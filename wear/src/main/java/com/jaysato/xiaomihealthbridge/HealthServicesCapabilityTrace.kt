package com.jaysato.xiaomihealthbridge

internal data class HealthServicesCapabilityCall(
    val name: String,
    val startedElapsedNanos: Long,
    val completedElapsedNanos: Long?,
    val outcome: String
)

internal data class HealthServicesCapabilitySnapshot(
    val results: List<String>,
    val calls: List<HealthServicesCapabilityCall>,
    val complete: Boolean
)

internal fun healthServicesCapabilityOutcome(result: String): String =
    if (result.startsWith("available:")) "completed" else result

internal class HealthServicesCapabilityTrace(
    private val elapsedNanos: () -> Long,
    private val expectedNames: Set<String> = setOf("Measure", "Passive", "Exercise")
) {
    private val calls = linkedMapOf<String, HealthServicesCapabilityCall>()
    private val results = mutableListOf<String>()
    private var sealed = false

    @Synchronized
    fun started(name: String): Boolean {
        if (sealed || name !in expectedNames || name in calls) return false
        calls[name] = HealthServicesCapabilityCall(
            name = name,
            startedElapsedNanos = elapsedNanos(),
            completedElapsedNanos = null,
            outcome = "pending"
        )
        return true
    }

    @Synchronized
    fun completed(name: String, result: String, outcome: String): Boolean {
        if (sealed) return false
        val current = calls[name] ?: return false
        if (current.completedElapsedNanos != null) return false
        calls[name] = current.copy(
            completedElapsedNanos = elapsedNanos(),
            outcome = outcome.take(80)
        )
        results.add("$name: $result")
        return isCompleteLocked()
    }

    @Synchronized
    fun freezeSnapshot(): HealthServicesCapabilitySnapshot {
        val complete = isCompleteLocked()
        if (!sealed) {
            if (!complete) {
                calls.entries.toList().forEach { (name, call) ->
                    if (call.completedElapsedNanos == null && call.outcome == "pending") {
                        calls[name] = call.copy(outcome = "pending_at_response_timeout")
                    }
                }
            }
            sealed = true
        }
        return HealthServicesCapabilitySnapshot(
            results = results.toList(),
            calls = calls.values.toList(),
            complete = complete
        )
    }

    @Synchronized
    fun snapshot(): List<HealthServicesCapabilityCall> = calls.values.toList()

    private fun isCompleteLocked(): Boolean =
        calls.keys == expectedNames && calls.values.all { it.completedElapsedNanos != null }
}
