package com.t2v.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun byId(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobooks WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeForProject(projectId: Long): Flow<List<AudiobookEntity>>

    @Query("SELECT * FROM audiobooks WHERE id = :id")
    suspend fun byId(id: Long): AudiobookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audiobook: AudiobookEntity): Long

    @Update
    suspend fun update(audiobook: AudiobookEntity)

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM audiobooks WHERE projectId = :projectId")
    suspend fun nextOrderIndex(projectId: Long): Int
}

@Dao
interface AudioTimelineDao {
    @Query("SELECT * FROM audio_tracks WHERE audiobookId = :audiobookId ORDER BY orderIndex")
    suspend fun tracks(audiobookId: Long): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_clips WHERE trackId = :trackId ORDER BY timelineStartMs")
    suspend fun clips(trackId: String): List<AudioClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<AudioTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClips(clips: List<AudioClipEntity>)

    @Query("DELETE FROM audio_tracks WHERE audiobookId = :audiobookId")
    suspend fun deleteTimeline(audiobookId: Long)

    @Query("SELECT * FROM audio_tracks WHERE audiobookId = :audiobookId AND type = :type LIMIT 1")
    suspend fun trackByType(audiobookId: Long, type: String): AudioTrackEntity?
}

@Dao
interface ChapterExportDao {
    @Query("SELECT * FROM chapter_exports WHERE audiobookId = :audiobookId ORDER BY createdAt DESC")
    fun observeForAudiobook(audiobookId: Long): Flow<List<ChapterExportEntity>>

    @Insert
    suspend fun insert(value: ChapterExportEntity): Long

    @Delete
    suspend fun delete(value: ChapterExportEntity)
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segments WHERE audiobookId = :audiobookId ORDER BY orderIndex ASC")
    fun observeForAudiobook(audiobookId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE audiobookId = :audiobookId ORDER BY orderIndex ASC")
    suspend fun listForAudiobook(audiobookId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE id = :id")
    suspend fun byId(id: Long): SegmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(segment: SegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(segments: List<SegmentEntity>)

    @Update
    suspend fun update(segment: SegmentEntity)
}

@Dao
interface VoiceDao {
    @Query("SELECT * FROM voices WHERE engineId = :engineId ORDER BY displayName")
    fun observeForEngine(engineId: String): Flow<List<VoiceEntity>>

    @Query("SELECT * FROM voices WHERE engineId = :engineId AND voiceId = :voiceId LIMIT 1")
    suspend fun find(engineId: String, voiceId: String): VoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(voice: VoiceEntity): Long

    @Delete
    suspend fun delete(voice: VoiceEntity)
}

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary WHERE language = :language OR language = 'all'")
    suspend fun list(language: String): List<DictionaryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DictionaryEntry): Long

    @Delete
    suspend fun delete(entry: DictionaryEntry)
}

@Dao
interface NormalizationDao {
    @Query("SELECT * FROM normalization_rules")
    suspend fun list(): List<NormalizationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: NormalizationRuleEntity)
}
