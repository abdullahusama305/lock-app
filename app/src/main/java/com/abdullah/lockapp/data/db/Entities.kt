package com.abdullah.lockapp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val gateEnabled: Boolean,
    val dailyLimitMinutes: Int?,
    val addedAt: Long,
)

@Entity(tableName = "questions", indices = [Index("category")])
data class QuestionEntity(
    /** See QuestionIds: a hash of the normalised prompt and answer, shared by every source. */
    @PrimaryKey val id: String,
    val source: String,
    /** A QuestionCategory name. */
    val category: String,
    /** 1 easy … 3 hard, 0 unknown. */
    val difficulty: Int,
    val passage: String?,
    val prompt: String,
    val correct: String,
    val wrong: List<String>,
    val explanation: String?,
    val timesShown: Int = 0,
    val timesCorrect: Int = 0,
    val lastShownAt: Long? = null,
    val lastCorrect: Boolean? = null,
    val flagged: Boolean = false,
)

@Entity(tableName = "gate_events", indices = [Index("day")])
data class GateEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Local epoch day of [timestamp]. */
    val day: Long,
    /** An EventType name. */
    val type: String,
    val packageName: String?,
    val answered: Int = 0,
    val correct: Int = 0,
)

@Entity(tableName = "daily_usage", primaryKeys = ["day", "packageName"])
data class DailyUsageEntity(
    val day: Long,
    val packageName: String,
    val minutes: Int,
)

@Entity(tableName = "pending_changes", indices = [Index(value = ["changeKey"], unique = true)])
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val changeKey: String,
    /** The serialized Change. */
    val payload: String,
    val description: String,
    val packageName: String?,
    val createdAt: Long,
    val effectiveAt: Long,
)

data class CategoryCount(val category: String, val n: Int)
