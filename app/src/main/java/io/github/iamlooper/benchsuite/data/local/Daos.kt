package io.github.iamlooper.benchsuite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Query("SELECT * FROM local_runs ORDER BY completedAt DESC")
    fun getAllRuns(): Flow<List<LocalRunEntity>>

    @Query("SELECT * FROM local_runs WHERE id = :runId")
    suspend fun getRunById(runId: String): LocalRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: LocalRunEntity)

    @Query("UPDATE local_runs SET isUploaded = 1 WHERE id = :runId")
    suspend fun markUploaded(runId: String)

    @Query("DELETE FROM local_runs WHERE id = :runId")
    suspend fun deleteRun(runId: String)

    @Query("DELETE FROM local_runs")
    suspend fun deleteAllRuns()
}

@Dao
interface ResultDao {
    @Query("SELECT * FROM local_results WHERE runId = :runId")
    suspend fun getResultsForRun(runId: String): List<LocalResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<LocalResultEntity>)

    @Query("SELECT * FROM local_category_scores WHERE runId = :runId")
    suspend fun getCategoryScoresForRun(runId: String): List<LocalCategoryScoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryScores(scores: List<LocalCategoryScoreEntity>)
}
