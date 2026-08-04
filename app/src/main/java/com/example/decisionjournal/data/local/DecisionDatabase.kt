package com.example.decisionjournal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.Review

@Database(entities = [Decision::class, Choice::class, Review::class], version = 5, exportSchema = false)
@TypeConverters(DecisionConverters::class)
abstract class DecisionDatabase : RoomDatabase() {
    abstract fun decisionDao(): DecisionDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decisions ADD COLUMN benefits TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE decisions ADD COLUMN concerns TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE decisions ADD COLUMN futureNote TEXT")
                db.execSQL("ALTER TABLE choices ADD COLUMN benefits TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE choices ADD COLUMN concerns TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decisions ADD COLUMN expectedOutcome TEXT")
                db.execSQL("ALTER TABLE decisions ADD COLUMN confidence INTEGER")
                db.execSQL("ALTER TABLE reviews ADD COLUMN expectationMatch TEXT")
                db.execSQL("ALTER TABLE reviews ADD COLUMN accurateJudgment TEXT")
                db.execSQL("ALTER TABLE reviews ADD COLUMN unexpectedFinding TEXT")
                db.execSQL("ALTER TABLE reviews ADD COLUMN nextTimeNote TEXT")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decisions ADD COLUMN decisionDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE decisions SET decisionDate = createdAt WHERE decisionDate = 0")
            }
        }
    }
}
