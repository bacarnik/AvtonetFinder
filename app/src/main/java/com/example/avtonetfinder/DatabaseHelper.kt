package com.example.avtonetfinder

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "avtonet_finder.db"
        private const val DATABASE_VERSION = 3

        const val TABLE_SEARCHES = "searches"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_URL = "url"
        const val COLUMN_ENABLED = "is_enabled"
        const val COLUMN_LAST_CHECKED = "last_checked"
        const val COLUMN_LAST_RESULT = "last_result"

        const val TABLE_SEEN = "seen_listings"
        const val COLUMN_LISTING_ID = "listing_id"
        const val COLUMN_TIMESTAMP = "timestamp"

        const val TABLE_HISTORY = "notification_history"
        const val COLUMN_HISTORY_TITLE = "title"
        const val COLUMN_HISTORY_URL = "url"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_SEARCHES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_URL TEXT,
                $COLUMN_ENABLED INTEGER DEFAULT 1,
                $COLUMN_LAST_CHECKED INTEGER DEFAULT 0,
                $COLUMN_LAST_RESULT TEXT
            )
        """)
        db.execSQL("""
            CREATE TABLE $TABLE_SEEN (
                $COLUMN_LISTING_ID TEXT PRIMARY KEY,
                $COLUMN_TIMESTAMP INTEGER
            )
        """)
        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_HISTORY_TITLE TEXT,
                $COLUMN_HISTORY_URL TEXT,
                $COLUMN_TIMESTAMP INTEGER
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_HISTORY ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_HISTORY_TITLE TEXT, $COLUMN_HISTORY_URL TEXT, $COLUMN_TIMESTAMP INTEGER)")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_SEARCHES ADD COLUMN $COLUMN_LAST_RESULT TEXT")
        }
    }

    fun addSearch(name: String, url: String) {
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_URL, url)
        }
        writableDatabase.insert(TABLE_SEARCHES, null, values)
    }

    fun getAllSearches(): List<Search> {
        val list = mutableListOf<Search>()
        val cursor = readableDatabase.query(TABLE_SEARCHES, null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            list.add(Search(
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_CHECKED)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_RESULT))
            ))
        }
        cursor.close()
        return list
    }

    fun updateSearchEnabled(id: Int, enabled: Boolean) {
        val values = ContentValues().apply { put(COLUMN_ENABLED, if (enabled) 1 else 0) }
        writableDatabase.update(TABLE_SEARCHES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun updateLastChecked(id: Int, timestamp: Long) {
        val values = ContentValues().apply { put(COLUMN_LAST_CHECKED, timestamp) }
        writableDatabase.update(TABLE_SEARCHES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun updateLastResult(id: Int, result: String) {
        val values = ContentValues().apply { put(COLUMN_LAST_RESULT, result) }
        writableDatabase.update(TABLE_SEARCHES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun deleteSearch(id: Int) {
        writableDatabase.delete(TABLE_SEARCHES, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun updateSearch(id: Int, name: String, url: String) {
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_URL, url)
        }
        writableDatabase.update(TABLE_SEARCHES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun isFirstScan(searchId: Int): Boolean {
        val cursor = readableDatabase.query(
            TABLE_SEARCHES,
            arrayOf(COLUMN_LAST_CHECKED),
            "$COLUMN_ID = ?",
            arrayOf(searchId.toString()),
            null, null, null
        )
        var isFirst = true
        if (cursor.moveToFirst()) {
            val lastChecked = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_CHECKED))
            isFirst = lastChecked == 0L
        }
        cursor.close()
        return isFirst
    }

    fun isListingSeen(listingId: String): Boolean {
        val cursor = readableDatabase.query(TABLE_SEEN, null, "$COLUMN_LISTING_ID = ?", arrayOf(listingId), null, null, null)
        val seen = cursor.count > 0
        cursor.close()
        return seen
    }

    fun markListingAsSeen(listingId: String) {
        val values = ContentValues().apply { 
            put(COLUMN_LISTING_ID, listingId)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(TABLE_SEEN, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun addNotificationHistory(title: String, url: String) {
        val values = ContentValues().apply {
            put(COLUMN_HISTORY_TITLE, title)
            put(COLUMN_HISTORY_URL, url)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE_HISTORY, null, values)
    }

    fun getNotificationHistory(): List<NotificationHistory> {
        val list = mutableListOf<NotificationHistory>()
        val cursor = readableDatabase.query(TABLE_HISTORY, null, null, null, null, null, "$COLUMN_TIMESTAMP DESC")
        while (cursor.moveToNext()) {
            list.add(NotificationHistory(
                cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_TITLE)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HISTORY_URL)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
            ))
        }
        cursor.close()
        return list
    }
}

data class Search(
    val id: Int,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
    val lastChecked: Long,
    val lastResult: String? = null
)

data class NotificationHistory(
    val id: Int,
    val title: String,
    val url: String,
    val timestamp: Long
)
