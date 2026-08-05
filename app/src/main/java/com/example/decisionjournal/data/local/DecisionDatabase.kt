package com.example.decisionjournal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.Review
import java.time.Instant
import java.time.ZoneId

@Database(entities = [Decision::class, Choice::class, Review::class], version = 10, exportSchema = true)
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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_choices_decisionId ON choices(decisionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reviews_decisionId ON reviews(decisionId)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_decisions_decisionDate ON decisions(decisionDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_decisions_reviewDate_status ON decisions(reviewDate, status)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decisions ADD COLUMN reminderState TEXT NOT NULL DEFAULT 'NOT_APPLICABLE'")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing reviewDate values are calendar days. The repository rebuilds their
                // notification work at the evening reminder time on the next app start.
                db.execSQL("ALTER TABLE decisions ADD COLUMN reminderAt INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decisions ADD COLUMN reviewDateKey TEXT")
                val zone = ZoneId.systemDefault()
                db.query("SELECT id, reviewDate FROM decisions WHERE reviewDate IS NOT NULL").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val dateIndex = cursor.getColumnIndexOrThrow("reviewDate")
                    while (cursor.moveToNext()) {
                        val key = Instant.ofEpochMilli(cursor.getLong(dateIndex)).atZone(zone).toLocalDate().toString()
                        db.execSQL("UPDATE decisions SET reviewDateKey = ? WHERE id = ?", arrayOf<Any>(key, cursor.getLong(idIndex)))
                    }
                }
            }
        }
    }
}
