package com.k4music.android.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSnapshotTest {
    @Test
    fun nativeSnapshotIsSelectedOnlyWhenTheSqliteVersionSupportsIt() {
        assertFalse(supportsVacuumSnapshot("3.22.0"))
        assertFalse(supportsVacuumSnapshot("3.26.0"))
        assertTrue(supportsVacuumSnapshot("3.27.0"))
        assertTrue(supportsVacuumSnapshot("3.46.1"))
        assertTrue(supportsVacuumSnapshot("4.0.0"))
        assertFalse(supportsVacuumSnapshot("unknown"))
    }
}
