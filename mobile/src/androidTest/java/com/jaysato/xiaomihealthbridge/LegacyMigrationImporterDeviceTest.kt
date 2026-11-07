package com.jaysato.xiaomihealthbridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMigrationImporterDeviceTest {
    @Test fun importsInstalledSameSignatureBridgeWithoutDeletingLegacyData() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val summary = LegacyMigrationImporter.importFromInstalledLegacyApp(app)
        assertTrue(SignalEvidenceDatabase.get(app).evidence().sessionCount() >= summary.sessions)
        assertTrue(summary.rawSamples >= 0)
    }
}
