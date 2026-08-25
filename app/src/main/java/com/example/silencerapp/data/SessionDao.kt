package com.example.silencerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: StudySession)

    @Query("SELECT * FROM study_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Query("SELECT SUM(durationMinutes) FROM study_sessions")
    fun getTotalStudyTime(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM study_sessions")
    fun getSessionCount(): Flow<Int>

    @Query("SELECT SUM(durationMinutes) FROM study_sessions WHERE startTime >= :since")
    fun getStudyTimeSince(since: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM study_sessions WHERE startTime >= :since")
    fun getSessionCountSince(since: Long): Flow<Int>
}
