package com.k4music.android.utils

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.RandomAccessFile

/**
 * Export through SQLite under one transaction, including on Android versions without VACUUM INTO.
 * Never copy pages out of an open main database: committed pages may still live in its WAL.
 */
internal fun snapshotDatabase(source: SupportSQLiteDatabase, target: File) {
    check(target.length() == 0L) { "Snapshot destination must be empty" }
    val version = source.query("SELECT sqlite_version()").use { if (it.moveToFirst()) it.getString(0) else "" }
    if (supportsVacuumSnapshot(version)) {
        source.execSQL("VACUUM INTO '${target.absolutePath.replace("'", "''")}'")
        RandomAccessFile(target, "rw").use { it.fd.sync() }
        return
    }
    val flags = SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    SQLiteDatabase.openDatabase(target.absolutePath, null, flags).use { destination ->
        source.beginTransactionNonExclusive()
        try {
            val schema = source.query(
                "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY type, name"
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
            }
            destination.beginTransaction()
            try {
                schema.filter { it.first == "table" }.forEach { (_, name, sql) ->
                    destination.execSQL(sql)
                    copyTable(source, destination, name)
                }
                // Preserve AUTOINCREMENT high-water marks even when the highest rows were deleted.
                source.query("SELECT 1 FROM sqlite_master WHERE name='sqlite_sequence'").use {
                    if (it.moveToFirst()) {
                        destination.execSQL("DELETE FROM sqlite_sequence")
                        copyTable(source, destination, "sqlite_sequence")
                    }
                }
                schema.filter { it.first != "table" }.forEach { (_, _, sql) -> destination.execSQL(sql) }
                destination.version = source.version
                destination.setTransactionSuccessful()
            } finally { destination.endTransaction() }
            source.setTransactionSuccessful()
        } finally { source.endTransaction() }
    }
    RandomAccessFile(target, "rw").use { it.fd.sync() }
}

private fun copyTable(source: SupportSQLiteDatabase, destination: SQLiteDatabase, name: String) {
    val quoted = "\"${name.replace("\"", "\"\"")}\""
    source.query("SELECT * FROM $quoted").use { rows ->
        val values = List(rows.columnCount) { "?" }.joinToString(",")
        destination.compileStatement("INSERT INTO $quoted VALUES ($values)").use { insert ->
            while (rows.moveToNext()) {
                insert.clearBindings()
                for (column in 0 until rows.columnCount) {
                    val index = column + 1
                    when (rows.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> insert.bindNull(index)
                        Cursor.FIELD_TYPE_INTEGER -> insert.bindLong(index, rows.getLong(column))
                        Cursor.FIELD_TYPE_FLOAT -> insert.bindDouble(index, rows.getDouble(column))
                        Cursor.FIELD_TYPE_BLOB -> insert.bindBlob(index, rows.getBlob(column))
                        else -> insert.bindString(index, rows.getString(column))
                    }
                }
                insert.executeInsert()
            }
        }
    }
}

/** VACUUM INTO was introduced in SQLite 3.27; older Android releases use the transactional exporter. */
internal fun supportsVacuumSnapshot(version: String): Boolean {
    val parts = version.split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
    return major > 3 || (major == 3 && minor >= 27)
}
