package com.jaysato.xiaomihealthbridge

internal object SensorRegistrationGuard {
    fun <T> create(factory: () -> T, onFailure: (RuntimeException) -> Unit): T? = try {
        factory()
    } catch (error: RuntimeException) {
        onFailure(error)
        null
    }

    fun terminalFailureReason(recordedReason: String, fallback: String = "registration_failed"): String =
        recordedReason.takeUnless { it.isBlank() || it == "unknown" || it == "running" } ?: fallback

    fun runPrimaryThenFallback(
        primary: () -> Boolean,
        fallback: () -> Boolean,
        onPrimaryRuntimeFailure: (RuntimeException) -> Unit
    ): Boolean {
        var primaryFailure: RuntimeException? = null
        val primaryStarted = try {
            primary()
        } catch (error: SecurityException) {
            throw error
        } catch (error: RuntimeException) {
            primaryFailure = error
            onPrimaryRuntimeFailure(error)
            false
        }
        if (primaryStarted) return true
        val fallbackStarted = fallback()
        if (!fallbackStarted) primaryFailure?.let { throw it }
        return fallbackStarted
    }

    fun run(
        register: () -> Boolean,
        onPermissionDenied: (SecurityException) -> Boolean,
        onRuntimeFailure: (RuntimeException) -> Boolean
    ): Boolean = try {
        register()
    } catch (error: SecurityException) {
        onPermissionDenied(error)
    } catch (error: RuntimeException) {
        onRuntimeFailure(error)
    }

    fun cleanup(actions: List<() -> Unit>, onFailure: (RuntimeException) -> Unit) {
        actions.forEach { action -> cleanupSucceeded(action, onFailure) }
    }

    fun cleanupSucceeded(action: () -> Unit, onFailure: (RuntimeException) -> Unit): Boolean = try {
        action()
        true
    } catch (error: RuntimeException) {
        onFailure(error)
        false
    }

    fun runAfterCleanup(
        cleanup: () -> Unit,
        register: () -> Boolean,
        onCleanupFailure: (RuntimeException) -> Unit,
        onPermissionDenied: (SecurityException) -> Boolean,
        onRuntimeFailure: (RuntimeException) -> Boolean
    ): Boolean {
        if (!cleanupSucceeded(cleanup, onCleanupFailure)) return false
        return run(register, onPermissionDenied, onRuntimeFailure)
    }
}
