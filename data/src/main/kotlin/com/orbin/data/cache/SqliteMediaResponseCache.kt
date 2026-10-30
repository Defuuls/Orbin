package com.orbin.data.cache

import androidx.sqlite.db.SupportSQLiteDatabase
import com.orbin.network.MediaResponseCache
import okio.Buffer
import okio.Source
import okio.Timeout
import java.io.IOException
import java.util.UUID

/** Chunked media store over an encrypted SQLCipher database; no whole-file byte arrays. */
internal class SqliteMediaResponseCache(
    private val database: () -> SupportSQLiteDatabase,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
) : MediaResponseCache {
    private val readers = mutableMapOf<String, Int>()
    private val writers = mutableSetOf<String>()
    private var generation = 0L

    @Synchronized
    override fun open(
        key: String,
        nowMillis: Long,
    ): MediaResponseCache.Entry? {
        val db = database()
        val row =
            db
                .query(
                    "SELECT token, content_type, bytes, expires, created, status, content_range FROM media_entries " +
                        "WHERE cache_key = ? AND complete = 1 ORDER BY touched DESC",
                    arrayOf<Any?>(key),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    Row(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        cursor.getLong(EXPIRES_COLUMN),
                        cursor.getLong(CREATED_COLUMN),
                        cursor.getInt(STATUS_COLUMN),
                        cursor.getString(RANGE_COLUMN),
                    )
                }
        if (row.expires <= nowMillis) {
            if (row.token !in readers) delete(row.token)
            return null
        }
        readers[row.token] = (readers[row.token] ?: 0) + 1
        db.execSQL("UPDATE media_entries SET touched = ? WHERE token = ?", arrayOf<Any?>(nowMillis, row.token))
        return MediaResponseCache.Entry(
            row.type,
            row.bytes,
            object : Source {
                private var sequence = 0
                private var remaining = row.bytes
                private val pending = Buffer()
                private var closed = false

                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long =
                    synchronized(this@SqliteMediaResponseCache) {
                        check(!closed) { "closed" }
                        require(byteCount >= 0)
                        if (byteCount == 0L) return@synchronized 0L
                        if (remaining == 0L) return@synchronized -1L
                        if (pending.size == 0L) {
                            val bytes =
                                db
                                    .query(
                                        "SELECT body FROM media_chunks WHERE token = ? AND sequence = ?",
                                        arrayOf<Any?>(row.token, sequence++),
                                    ).use { cursor ->
                                        if (!cursor.moveToFirst()) {
                                            throw IOException(
                                                "Cached media was cleared or incomplete",
                                            )
                                        }
                                        cursor.getBlob(0)
                                    }
                            pending.write(bytes)
                        }
                        val count = pending.read(sink, minOf(byteCount, remaining))
                        remaining -= count
                        count
                    }

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() =
                    synchronized(this@SqliteMediaResponseCache) {
                        if (!closed) {
                            closed = true
                            pending.clear()
                            val count = (readers[row.token] ?: 1) - 1
                            if (count == 0) readers.remove(row.token) else readers[row.token] = count
                        }
                    }
            },
            row.created,
            row.status,
            row.range,
        )
    }

    @Synchronized
    override fun start(
        key: String,
        contentType: String,
        expiresAtMillis: Long,
        statusCode: Int,
        contentRange: String?,
    ): MediaResponseCache.Write {
        val db = database()
        val token = UUID.randomUUID().toString()
        val epoch = generation
        db.execSQL(
            "INSERT INTO media_entries(token, cache_key, content_type, expires, touched, created, " +
                "status, content_range, bytes, complete) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0, 0)",
            arrayOf<Any?>(
                token,
                key,
                contentType,
                expiresAtMillis,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                statusCode,
                contentRange,
            ),
        )
        writers.add(token)
        return object : MediaResponseCache.Write {
            private var sequence = 0
            private var bytes = 0L
            private var finished = false

            override fun append(bytes: ByteArray) =
                synchronized(this@SqliteMediaResponseCache) {
                    checkOpen()
                    if (bytes.size > CHUNK_BYTES || this.bytes + bytes.size > maxEntryBytes) {
                        abort()
                        throw IOException("Media exceeds the cache entry budget")
                    }
                    makeRoom(bytes.size.toLong(), token)
                    db.beginTransaction()
                    try {
                        db.execSQL(
                            "INSERT INTO media_chunks(token, sequence, body) VALUES(?, ?, ?)",
                            arrayOf<Any?>(token, sequence++, bytes),
                        )
                        this.bytes += bytes.size
                        db.execSQL(
                            "UPDATE media_entries SET bytes = ? WHERE token = ?",
                            arrayOf<Any?>(this.bytes, token),
                        )
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }

            override fun commit(): Unit =
                synchronized(this@SqliteMediaResponseCache) {
                    checkOpen()
                    db.beginTransaction()
                    try {
                        val replaced =
                            db
                                .query(
                                    "SELECT token FROM media_entries WHERE cache_key = ? AND token != ? AND complete = 1",
                                    arrayOf<Any?>(key, token),
                                ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
                        replaced.forEach(::retire)
                        db.execSQL("UPDATE media_entries SET complete = 1 WHERE token = ?", arrayOf<Any?>(token))
                        db.setTransactionSuccessful()
                        finished = true
                        writers.remove(token)
                    } finally {
                        db.endTransaction()
                    }
                }

            override fun abort() =
                synchronized(this@SqliteMediaResponseCache) {
                    if (!finished) {
                        delete(token)
                        writers.remove(token)
                        finished = true
                    }
                }

            private fun checkOpen() {
                if (finished || epoch != generation) throw IOException("Cache write was canceled")
            }
        }
    }

    @Synchronized
    override fun clear() {
        generation++
        val db = database()
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM media_chunks")
            db.execSQL("DELETE FROM media_entries")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        writers.clear()
    }

    @Synchronized
    override fun usageBytes(): Long =
        database().query("SELECT COALESCE(SUM(bytes), 0) FROM media_entries").use {
            it.moveToFirst()
            it.getLong(0)
        }

    private fun makeRoom(
        incoming: Long,
        current: String,
    ) {
        while (usageBytes() + incoming > maxBytes) {
            val candidate = evictionCandidate(current) ?: throw IOException("Media cache is full")
            delete(candidate)
        }
    }

    private fun evictionCandidate(current: String): String? =
        database().query("SELECT token FROM media_entries ORDER BY touched ASC").use { cursor ->
            var found: String? = null
            while (cursor.moveToNext() && found == null) {
                val token = cursor.getString(0)
                if (token != current && token !in writers && token !in readers) found = token
            }
            found
        }

    private fun retire(token: String) {
        if (token in readers) {
            database().execSQL("UPDATE media_entries SET complete = 0 WHERE token = ?", arrayOf<Any?>(token))
        } else {
            delete(token)
        }
    }

    private fun delete(token: String) {
        val db = database()
        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) db.beginTransaction()
        try {
            db.execSQL("DELETE FROM media_chunks WHERE token = ?", arrayOf<Any?>(token))
            db.execSQL("DELETE FROM media_entries WHERE token = ?", arrayOf<Any?>(token))
            if (ownsTransaction) db.setTransactionSuccessful()
        } finally {
            if (ownsTransaction) db.endTransaction()
        }
    }

    private data class Row(
        val token: String,
        val type: String,
        val bytes: Long,
        val expires: Long,
        val created: Long,
        val status: Int,
        val range: String?,
    )

    companion object {
        private const val EXPIRES_COLUMN = 3
        private const val CREATED_COLUMN = 4
        private const val STATUS_COLUMN = 5
        private const val RANGE_COLUMN = 6
        const val NAME = "orbin_media_cache.db"
        private const val DEFAULT_MAX_BYTES = 128L * 1024L * 1024L
        private const val DEFAULT_MAX_ENTRY_BYTES = 32L * 1024L * 1024L
        private const val CHUNK_BYTES = 64 * 1024

        fun createSchema(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA auto_vacuum = FULL")
            db.query("PRAGMA max_page_count = 65536").close()
            db.execSQL(
                "CREATE TABLE media_entries(token TEXT PRIMARY KEY, cache_key TEXT NOT NULL, content_type TEXT NOT NULL, expires INTEGER NOT NULL, touched INTEGER NOT NULL, created INTEGER NOT NULL, status INTEGER NOT NULL, content_range TEXT, bytes INTEGER NOT NULL, complete INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX media_lookup ON media_entries(cache_key, complete)")
            db.execSQL(
                "CREATE TABLE media_chunks(token TEXT NOT NULL, sequence INTEGER NOT NULL, body BLOB NOT NULL, PRIMARY KEY(token, sequence))",
            )
        }
    }
}
