package com.abdullah.lockapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json

@Database(
    entities = [
        AppRuleEntity::class,
        QuestionEntity::class,
        GateEventEntity::class,
        DailyUsageEntity::class,
        PendingChangeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appRules(): AppRuleDao
    abstract fun questions(): QuestionDao
    abstract fun events(): GateEventDao
    abstract fun usage(): DailyUsageDao
    abstract fun pending(): PendingChangeDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lockapp.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toList(value: String): List<String> = Json.decodeFromString(value)
}
