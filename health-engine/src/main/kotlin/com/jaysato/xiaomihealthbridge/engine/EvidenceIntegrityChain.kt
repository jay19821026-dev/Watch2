package com.jaysato.xiaomihealthbridge.engine

import java.security.MessageDigest

data class EvidenceEnvelope(
    val sequence: Long,
    val sensor: String,
    val payload: String,
    val source: String,
    val algorithmVersion: String,
    val previousHash: String,
    val committedHash: String
) {
    val hash: String get() = committedHash
    fun recomputedHash(): String = EvidenceIntegrityChain.hash("$sequence|$sensor|$payload|$source|$algorithmVersion|$previousHash")
}

data class IntegrityCheck(val valid: Boolean, val reason: String, val lastHash: String)

object EvidenceIntegrityChain {
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun append(
        previousHash: String,
        sequence: Long,
        sensor: String,
        payload: String,
        source: String,
        algorithmVersion: String
    ): EvidenceEnvelope {
        require(sequence >= 0 && sensor.isNotBlank() && source.isNotBlank() && algorithmVersion.isNotBlank())
        val content = "$sequence|$sensor|$payload|$source|$algorithmVersion|$previousHash"
        return EvidenceEnvelope(sequence, sensor, payload, source, algorithmVersion, previousHash, hash(content))
    }

    fun verify(envelopes: List<EvidenceEnvelope>): IntegrityCheck {
        var previous = ""
        for ((index, envelope) in envelopes.withIndex()) {
            if (envelope.sequence != index.toLong()) return IntegrityCheck(false, "SEQUENCE_CHANGED", previous)
            if (envelope.previousHash != previous) return IntegrityCheck(false, "PREVIOUS_HASH_CHANGED", previous)
            if (envelope.committedHash != envelope.recomputedHash()) {
                return IntegrityCheck(false, "PAYLOAD_OR_METADATA_CHANGED", previous)
            }
            previous = envelope.hash
        }
        return IntegrityCheck(true, "CHAIN_VERIFIED", previous)
    }
}
