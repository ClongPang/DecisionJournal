package com.example.decisionjournal.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.decisionjournal.data.model.Choice
import com.example.decisionjournal.data.model.Decision
import com.example.decisionjournal.data.model.Review

@Database(entities = [Decision::class, Choice::class, Review::class], version = 3, exportSchema = false)
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
    }
}
