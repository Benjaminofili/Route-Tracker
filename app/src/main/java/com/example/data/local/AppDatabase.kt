package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val polyline: String,
    val waypointsJson: String,
    val elevationGainMeters: Int = 0,
    val elevationLossMeters: Int = 0,
    val elevationProfileJson: String = "[]",
    val difficulty: String = "FLAT",
    val routeType: String = "PARKS",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "run_records")
data class RunRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgPaceSecondsPerKm: Int,
    val polyline: String,
    val pointsCount: Int,
    val elevationGainMeters: Int = 0,
    val elevationLossMeters: Int = 0,
    val elevationProfileJson: String = "[]",
    val difficulty: String = "Flat",
    val routeType: String = "Parks"
)

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    fun getAllRoutes(): Flow<List<SavedRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: SavedRouteEntity)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteRouteById(id: String)
}

@Dao
interface RunRecordDao {
    @Query("SELECT * FROM run_records ORDER BY timestamp DESC")
    fun getAllRuns(): Flow<List<RunRecordEntity>>

    @Query("SELECT * FROM run_records WHERE id = :id")
    suspend fun getRunById(id: Long): RunRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunRecordEntity): Long

    @Query("DELETE FROM run_records WHERE id = :id")
    suspend fun deleteRunById(id: Long)
}

@Database(
    entities = [SavedRouteEntity::class, RunRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedRouteDao(): SavedRouteDao
    abstract fun runRecordDao(): RunRecordDao
}
