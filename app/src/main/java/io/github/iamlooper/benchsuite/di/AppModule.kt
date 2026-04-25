package io.github.iamlooper.benchsuite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.iamlooper.benchsuite.BuildConfig
import io.github.iamlooper.benchsuite.data.local.AppDatabase
import io.github.iamlooper.benchsuite.data.local.settingsDataStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "benchsuite.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideRunDao(db: AppDatabase) = db.runDao()

    @Provides
    fun provideResultDao(db: AppDatabase) = db.resultDao()

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    ) {
        install(Functions)
        install(Postgrest)
    }
}
