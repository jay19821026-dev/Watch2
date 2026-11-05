package com.jaysato.xiaomihealthbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSnapshotEditorChangesTest {
    @Test fun lastOperationForEachKeyWins() {
        val editor = RawSnapshotEditorChanges()
        editor.put("raw", 1)
        editor.remove("raw")
        assertTrue("raw" in editor.drain().removals)
        editor.remove("raw")
        editor.put("raw", 2)
        val update = editor.drain()
        assertEquals(2, update.values["raw"])
        assertFalse("raw" in update.removals)
    }

    @Test fun clearIsAppliedBeforePendingValuesRegardlessOfCallOrder() {
        var persisted: Map<String, *> = mapOf("old" to 1)
        val store = RawSnapshotTransactions({ persisted }, { persisted = it })
        val editor = RawSnapshotEditorChanges()
        editor.put("new", 2)
        editor.clear()
        assertEquals(mapOf("new" to 2), store.update(editor.drain()))
    }

    @Test fun nullRemovesAndDrainingMakesEditorReusable() {
        val editor = RawSnapshotEditorChanges()
        editor.put("raw", 1)
        editor.put("raw", null)
        assertEquals(setOf("raw"), editor.drain().removals)
        val empty = editor.drain()
        assertFalse(empty.clear)
        assertTrue(empty.removals.isEmpty() && empty.values.isEmpty())
        editor.put("raw", 3)
        assertEquals(3, editor.drain().values["raw"])
    }

    @Test fun suppliedAndReturnedSetsAreDetached() {
        val original = mutableSetOf("a")
        val editor = RawSnapshotEditorChanges()
        editor.put("set", original)
        original.add("b")
        val update = editor.drain()
        assertEquals(setOf("a"), update.values["set"])
        (update.values["set"] as MutableSet<String>).add("c")
        editor.put("set", setOf("d"))
        assertEquals(setOf("d"), editor.drain().values["set"])
    }
}
