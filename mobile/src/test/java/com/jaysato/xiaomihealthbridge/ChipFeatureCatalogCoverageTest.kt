package com.jaysato.xiaomihealthbridge

import com.jaysato.xiaomihealthbridge.engine.HealthAlgorithmEngine as ClinicalSensorFormulas

import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies chip→feature coverage required by the dual-app goal without a physical phone.
 */
class ChipFeatureCatalogCoverageTest {
    @Test
    fun allRequiredChipsPresent() {
        val parts = ChipFeatureCatalog.chips.map { it.part }.toSet()
        assertTrue(parts.any { it.startsWith("MAX86178") })
        assertTrue(parts.any { it.startsWith("BOSCH bmi26x") })
        assertTrue(parts.any { it.startsWith("AKM ak0991x") })
        assertTrue(parts.any { it.startsWith("TDK icp101xx") })
        assertTrue(parts.any { it.startsWith("Lite-On ltr311") })
        assertTrue(parts.contains("LifeQ"))
    }

    @Test
    fun max86178CoversOpticalBiozEcgAndWearDetect() {
        val titles = ChipFeatureCatalog.chips.first { it.part.startsWith("MAX86178") }.features.map { it.title }
        assertTrue(titles.any { it.contains("PPG") })
        assertTrue(titles.any { it.contains("SpO") })
        assertTrue(titles.any { it.contains("ECG") })
        assertTrue(titles.any { it.contains("BioZ") || it.contains("體成分") })
        assertTrue(titles.any { it.contains("離腕") })
    }

    @Test
    fun formulaKindsHaveDetailTargets() {
        val kinds = ChipFeatureCatalog.chips.flatMap { it.features }.mapNotNull { it.kind }.toSet()
        assertTrue(kinds.containsAll(listOf(
            "ppg", "ecg", "bioz", "spo2_raw", "off_body",
            "body_temperature_raw", "board_temperature", "accelerometer", "gyroscope",
            "step_counter", "pressure", "light", "lifeq_ott", "hrv_research"
        )))
    }

    @Test
    fun capabilityCatalogDoesNotExposeUnimplementedBiozRespirationAlias() {
        val features = ChipFeatureCatalog.chips.flatMap { it.features }
        val stepCounter = features.first { it.kind == "step_counter" }

        assertTrue(stepCounter.otherWatchUse.contains("非當日總量"))
        assertTrue(stepCounter.phoneView.contains("Health Connect 彙總"))
        assertTrue(features.none { it.kind == "bioz_respiration" })
        assertTrue(WatchSensorCatalog.items.first { it.kind == "step_counter" }.label.contains("重開機後累積"))
        assertTrue(WatchSensorCatalog.items.first { it.kind == "bioz_respiration" }.label.contains("共用 BioZ 端點"))
    }

    @Test
    fun watchSensorCatalogGroupsByChip() {
        val groups = WatchSensorCatalog.items.map { it.group }.toSet()
        assertTrue(groups.containsAll(listOf("MAX86178", "BMI26x", "AK0991x", "ICP101xx", "LTR311", "LifeQ")))
        assertTrue(WatchSensorCatalog.items.filter { it.kind.contains("temperature") || it.kind == "thermometer" }
            .all { it.group == "型號未確認" })
        assertTrue(WatchSensorCatalog.items.any { it.kind == "hrv_research" })
        assertTrue(WatchSensorCatalog.items.any { it.kind == "bioz_respiration" })
        assertEquals("Xiaomi／MI", WatchSensorCatalog.items.first { it.kind == "step_counter" }.group)
        assertEquals("Xiaomi／MI", WatchSensorCatalog.items.first { it.kind == "off_body" }.group)
        assertEquals("融合", WatchSensorCatalog.items.first { it.kind == "orientation" }.group)
        assertEquals("LTR311", WatchSensorCatalog.items.first { it.kind == "light" }.group)
    }

    @Test
    fun stressScoreSyncedFieldNameStable() {
        // Contract used by WearDataProcessor + RawSensorDetailActivity
        assertEquals("formula_stress_score", "formula_stress_score")
        val score = ClinicalSensorFormulas.researchStressScore(0.05, 80.0)
        assertNotNull(score)
        assertTrue(score in 0..100)
    }
}
