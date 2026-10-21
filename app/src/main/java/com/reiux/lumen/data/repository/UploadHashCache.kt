package com.reiux.lumen.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadHashCache(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS file_hashes (
                account_email TEXT NOT NULL,
                sha1_b64 TEXT NOT NULL,
                media_key TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                mtime INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (account_email, sha1_b64, file_size)
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_file_hashes_acc ON file_hashes(account_email);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS file_hashes")
        onCreate(db)
    }

    suspend fun get(accountEmail: String, sha1B64: String, fileSize: Long): String? = withContext(Dispatchers.IO) {
        if (accountEmail.isBlank() || sha1B64.isBlank()) return@withContext null
        try {
            readableDatabase.rawQuery(
                "SELECT media_key FROM file_hashes WHERE account_email = ? AND sha1_b64 = ? AND file_size = ?",
                arrayOf(accountEmail, sha1B64, fileSize.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun put(accountEmail: String, sha1B64: String, mediaKey: String, fileSize: Long, mtime: Long) = withContext(Dispatchers.IO) {
        if (accountEmail.isBlank() || sha1B64.isBlank() || mediaKey.isBlank()) return@withContext
        try {
            val values = ContentValues().apply {
                put("account_email", accountEmail)
                put("sha1_b64", sha1B64)
                put("media_key", mediaKey)
                put("file_size", fileSize)
                put("mtime", mtime)
                put("created_at", System.currentTimeMillis() / 1000)
            }
            writableDatabase.insertWithOnConflict(
                "file_hashes",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val DATABASE_NAME = "lumen_hash_cache.db"
        private const val DATABASE_VERSION = 1
    }
}
