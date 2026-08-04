package com.example.decisionjournal.di

import android.content.Context
import androidx.room.Room
import com.example.decisionjournal.data.local.DecisionDao
import com.example.decisionjournal.data.local.DecisionDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DecisionDatabase =
        Room.databaseBuilder(context, DecisionDatabase::class.java, "decision-journal.db")
            .addMigrations(DecisionDatabase.MIGRATION_2_3, DecisionDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDecisionDao(database: DecisionDatabase): DecisionDao = database.decisionDao()
}
