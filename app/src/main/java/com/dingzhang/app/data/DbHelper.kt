package com.dingzhang.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 手写 SQLite，不上 Room/KSP：少一层注解处理器，构建链更短，也更容易在任意 JDK 上复现。
 * 表结构见 onCreate。金额存 INTEGER（分），时间存 INTEGER（epoch millis）。
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    // big_class：0 按金额自动判 / 1 用户指定为日常 / 2 用户指定为大额
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE txn (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_cents    INTEGER NOT NULL,
                kind            TEXT    NOT NULL,
                category_id     TEXT    NOT NULL,
                wallet          TEXT    NOT NULL,
                payee           TEXT    NOT NULL,
                note            TEXT    NOT NULL DEFAULT '',
                occurred_at     INTEGER NOT NULL,
                source          TEXT    NOT NULL,
                status          TEXT    NOT NULL,
                raw_text        TEXT    NOT NULL DEFAULT '',
                external_id     TEXT,
                category_locked INTEGER NOT NULL DEFAULT 0,
                account         TEXT    NOT NULL DEFAULT '',
                fingerprint     TEXT    NOT NULL DEFAULT '',
                big_class       INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_txn_time ON txn(occurred_at)")
        db.execSQL("CREATE INDEX idx_txn_cat ON txn(category_id)")
        db.execSQL("CREATE INDEX idx_txn_wallet ON txn(wallet, amount_cents)")

        db.execSQL(
            """
            CREATE TABLE rule (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                pattern     TEXT    NOT NULL,
                category_id TEXT    NOT NULL,
                kind        TEXT,
                hit_count   INTEGER NOT NULL DEFAULT 1,
                updated_at  INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX idx_rule_pattern ON rule(pattern)")

        db.execSQL(
            """
            CREATE TABLE import_job (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                file_name  TEXT NOT NULL,
                wallet     TEXT NOT NULL,
                total      INTEGER NOT NULL DEFAULT 0,
                inserted   INTEGER NOT NULL DEFAULT 0,
                matched    INTEGER NOT NULL DEFAULT 0,
                skipped    INTEGER NOT NULL DEFAULT 0,
                finished_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 已经有真账在里面了，只能往前加列，绝不能重建表。
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE txn ADD COLUMN big_class INTEGER NOT NULL DEFAULT 0")
        }
    }

    companion object {
        const val NAME = "dingzhang.db"
        const val VERSION = 2
    }
}
