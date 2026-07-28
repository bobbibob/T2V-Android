package com.t2v.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * База данных Room. Один файл `t2v.db`.
 *
 * Схема 1:1 повторяет SQLite из оригинала (audiobook_store.py):
 *   - projects
 *   - audiobooks
 *   - segments
 *   - voices (локальные, загруженные)
 *   - custom_dictionary
 *   - normalization_rules
 *   - voice_gallery_cache
 */
@Database(
    entities = [
        ProjectEntity::class,
        AudiobookEntity::class,
        SegmentEntity::class,
        VoiceEntity::class,
        DictionaryEntry::class,
        NormalizationRuleEntity::class,
        AudioTrackEntity::class,
        AudioClipEntity::class,
        ChapterExportEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun audiobooks(): AudiobookDao
    abstract fun segments(): SegmentDao
    abstract fun voices(): VoiceDao
    abstract fun dictionary(): DictionaryDao
    abstract fun normalization(): NormalizationDao
    abstract fun audioTimeline(): AudioTimelineDao
    abstract fun chapterExports(): ChapterExportDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "t2v.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN outputTreeUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN author TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN title TEXT NOT NULL DEFAULT 'Chapter'")
                db.execSQL("ALTER TABLE audiobooks ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audio_tracks (
                        id TEXT NOT NULL PRIMARY KEY,
                        audiobookId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        volumeDb REAL NOT NULL,
                        muted INTEGER NOT NULL,
                        solo INTEGER NOT NULL,
                        locked INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(audiobookId) REFERENCES audiobooks(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_tracks_audiobookId ON audio_tracks(audiobookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_tracks_audiobookId_type ON audio_tracks(audiobookId, type)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS audio_clips (
                        id TEXT NOT NULL PRIMARY KEY,
                        trackId TEXT NOT NULL,
                        sourcePath TEXT NOT NULL,
                        timelineStartMs INTEGER NOT NULL,
                        sourceStartMs INTEGER NOT NULL,
                        sourceEndMs INTEGER NOT NULL,
                        gainDb REAL NOT NULL,
                        speed REAL NOT NULL,
                        fadeInMs INTEGER NOT NULL,
                        fadeOutMs INTEGER NOT NULL,
                        loop INTEGER NOT NULL,
                        locked INTEGER NOT NULL,
                        markupTagId TEXT,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(trackId) REFERENCES audio_tracks(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_clips_trackId ON audio_clips(trackId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_clips_timelineStartMs ON audio_clips(timelineStartMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_clips_markupTagId ON audio_clips(markupTagId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chapter_exports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        audiobookId INTEGER NOT NULL,
                        displayName TEXT NOT NULL,
                        documentUri TEXT NOT NULL,
                        format TEXT NOT NULL,
                        bitrate TEXT NOT NULL,
                        durationMs INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isFinal INTEGER NOT NULL,
                        FOREIGN KEY(audiobookId) REFERENCES audiobooks(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chapter_exports_audiobookId ON chapter_exports(audiobookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chapter_exports_createdAt ON chapter_exports(createdAt)")
            }
        }
    }
}

class StringListConverter {
    @TypeConverter fun fromList(value: List<String>?): String =
        value?.joinToString("\u001F") ?: ""
    @TypeConverter fun toList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split("\u001F")
}
