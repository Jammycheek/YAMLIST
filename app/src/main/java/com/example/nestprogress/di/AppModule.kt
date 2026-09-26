package com.example.nestprogress.di

import android.content.Context
import androidx.room.Room
import com.example.nestprogress.data.local.NestDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): NestDatabase =
        Room.databaseBuilder(context, NestDatabase::class.java, NestDatabase.NAME)
            // FK enforcement so cascade & referential integrity hold (spec §7.3, §18.2).
            // No shipped release depends on the DB surviving a schema bump yet, so a
            // version change just recreates the local DB rather than needing a real
            // Migration object. Revisit once this matters to a real installed base —
            // at that point users should be told to back up (SCR-09) before updating.
            .fallbackToDestructiveMigration()
            .build()

    // NestRepository, YamlImportService and BackupManager are @Inject-constructed,
    // so Hilt provides them without explicit @Provides methods.
}
