package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.SourceHealth
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceHealthDao {

    @Query("SELECT * FROM source_health WHERE sourceUrl = :sourceUrl LIMIT 1")
    fun get(sourceUrl: String): SourceHealth?

    @Query("SELECT * FROM source_health")
    fun getAll(): List<SourceHealth>

    @Query("SELECT * FROM source_health")
    fun flowAll(): Flow<List<SourceHealth>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(health: SourceHealth)

    @Query("DELETE FROM source_health WHERE sourceUrl = :sourceUrl")
    fun clear(sourceUrl: String)
}
