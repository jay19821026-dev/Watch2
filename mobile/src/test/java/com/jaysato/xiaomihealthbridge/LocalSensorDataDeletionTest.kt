package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSensorDataDeletionTest {
    @Test
    fun onlyAnExistingEnumeratedDirectoryCountsAsSuccessfullyScanned() {
        assertFalse(LocalSensorDeletionPolicy.directoryListingFailed(false, false, false))
        assertTrue(LocalSensorDeletionPolicy.directoryListingFailed(true, false, false))
        assertTrue(LocalSensorDeletionPolicy.directoryListingFailed(true, true, false))
        assertFalse(LocalSensorDeletionPolicy.directoryListingFailed(true, true, true))
    }

    @Test
    fun localHealthDeletionIncludesRouteAndResearchCalibrationStores() {
        assertTrue(LocalSensorDataDeletion.preferenceFilesForDeletion.contains(WorkoutRouteStore.PREFS))
        assertTrue(LocalSensorDataDeletion.preferenceFilesForDeletion.contains(EcgResearchProfileStore.PREFS))
        assertTrue(LocalSensorDataDeletion.preferenceFilesForDeletion.contains(SensorCalibrationProfileStore.PREFS))
        assertTrue(LocalSensorDataDeletion.preferenceFilesForDeletion.contains(OpticalBpActivity.CUFF_PREFS))
        assertTrue(LocalSensorDataDeletion.preferenceFilesForDeletion.contains(RestingHeartRateEstimator.PREFS))
    }

    @Test
    fun deletionIncludesExactSuiteTemporaryArtifactWithoutMatchingUnrelatedNames() {
        assertTrue(LocalSensorDataDeletion.shouldDeleteExportName("sensor-suite-latest.json.tmp"))
        assertFalse(LocalSensorDataDeletion.shouldDeleteExportName("sensor-suite-latest.json.tmp.backup"))
        assertFalse(LocalSensorDataDeletion.shouldDeleteExportName("sensor-suite-latest.json.tmp-unrelated"))
        assertFalse(LocalSensorDataDeletion.shouldDeleteExportName("unrelated.json.tmp"))
        assertFalse(LocalSensorDataDeletion.shouldDeleteExportName(".sensor-suite-latest.json.keep"))
        assertFalse(LocalSensorDataDeletion.shouldDeleteExportName(".sensor-suite-latest.json.abc.tmp.backup"))
    }

    @Test
    fun deletionTargetsOnlyKnownSensorSnapshotAndExportNames() {
        assertTrue(LocalSensorDataDeletion.shouldDeleteExportName(SensorSuiteDump.FILE_NAME))
        assertTrue(LocalSensorDataDeletion.shouldDeleteExportName(LocalSensorDataDeletion.RAW_EXPORT_FILE_NAME))
        assertTrue(LocalSensorDataDeletion.shouldDeleteExportName("${LocalSensorDataDeletion.WATCH_RAW_EXPORT_PREFIX}123.json"))
        assertTrue(LocalSensorDataDeletion.shouldDeleteExportName(".${LocalSensorDataDeletion.WATCH_RAW_EXPORT_PREFIX}123.json.tmp"))
        assertTrue(LocalSensorDataDeletion.shouldDeleteExportName("${LocalSensorDataDeletion.SUMMARY_EXPORT_PREFIX}123.json"))
        assertTrue(LocalSensorDataDeletion.shouldDeleteExportName(".${SensorSuiteDump.FILE_NAME}.abc.tmp"))
        assertFalse(LocalSensorDataDeletion.shouldDeleteExportName("user-profile.json"))
        assertFalse(LocalSensorDataDeletion.shouldDeleteExportName("unrelated-export.json"))
    }
}
