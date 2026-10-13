package com.abdullah.lockapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAll(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    suspend fun get(pkg: String): AppRuleEntity?

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("UPDATE app_rules SET gateEnabled = :enabled WHERE packageName = :pkg")
    suspend fun setGate(pkg: String, enabled: Boolean)

    @Query("UPDATE app_rules SET dailyLimitMinutes = :minutes WHERE packageName = :pkg")
    suspend fun setLimit(pkg: String, minutes: Int?)
}

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<QuestionEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM questions WHERE flagged = 0 AND category != 'TEST'")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE flagged = 0 AND timesShown = 0 AND category != 'TEST'")
    fun observeUnseenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions WHERE flagged = 0 AND timesShown = 0 AND category = :category")
    suspend fun unseenCount(category: String): Int

    @Query("SELECT category, COUNT(*) AS n FROM questions WHERE flagged = 0 GROUP BY category")
    suspend fun countsByCategory(): List<CategoryCount>

    @Query(
        """SELECT * FROM questions
           WHERE flagged = 0 AND category = :category AND timesShown = 0
             AND difficulty IN (:difficulties) AND id NOT IN (:exclude)
           ORDER BY RANDOM() LIMIT 1""",
    )
    suspend fun randomUnseen(category: String, difficulties: List<Int>, exclude: List<String>): QuestionEntity?

    @Query(
        """SELECT * FROM questions
           WHERE flagged = 0 AND category = :category AND lastCorrect = 0
             AND lastShownAt < :shownBefore AND id NOT IN (:exclude)
           ORDER BY lastShownAt LIMIT 1""",
    )
    suspend fun oldestMissed(category: String, shownBefore: Long, exclude: List<String>): QuestionEntity?

    @Query(
        """SELECT * FROM questions
           WHERE flagged = 0 AND category = :category
             AND difficulty IN (:difficulties) AND id NOT IN (:exclude)
           ORDER BY lastShownAt ASC, RANDOM() LIMIT 1""",
    )
    suspend fun leastRecent(category: String, difficulties: List<Int>, exclude: List<String>): QuestionEntity?

    @Query(
        """UPDATE questions SET timesShown = timesShown + 1, timesCorrect = timesCorrect + :correctIncrement,
           lastShownAt = :now, lastCorrect = :correct WHERE id = :id""",
    )
    suspend fun recordAnswer(id: String, correct: Boolean, correctIncrement: Int, now: Long)

    @Query("UPDATE questions SET flagged = 1 WHERE id = :id")
    suspend fun flag(id: String)

    @Query("DELETE FROM questions WHERE category = 'TEST'")
    suspend fun deleteTestQuestions()
}

@Dao
interface GateEventDao {
    @Insert
    suspend fun insert(event: GateEventEntity)

    @Query("SELECT * FROM gate_events WHERE day >= :fromDay ORDER BY timestamp")
    fun observeSince(fromDay: Long): Flow<List<GateEventEntity>>
}

@Dao
interface DailyUsageDao {
    @Upsert
    suspend fun upsertAll(rows: List<DailyUsageEntity>)

    @Query("SELECT * FROM daily_usage WHERE day >= :fromDay")
    fun observeSince(fromDay: Long): Flow<List<DailyUsageEntity>>
}

@Dao
interface PendingChangeDao {
    @Query("SELECT * FROM pending_changes ORDER BY effectiveAt, createdAt")
    fun observeAll(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes WHERE effectiveAt <= :now ORDER BY createdAt")
    suspend fun due(now: Long): List<PendingChangeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(change: PendingChangeEntity)

    @Query("DELETE FROM pending_changes WHERE changeKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun delete(id: Long)
}
