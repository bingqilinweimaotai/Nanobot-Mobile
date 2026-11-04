package dev.nanobot.android.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val key: String,
    val chatId: String,
    val title: String,
    val preview: String,
    val updatedAt: String?,
    val projectPath: String? = null,
    val projectName: String? = null,
    val accessMode: String? = null,
    val restrictToWorkspace: Boolean? = null,
)

@Entity(tableName = "thread_cache")
data class ThreadCacheEntity(
    @PrimaryKey val chatId: String,
    val raw: String,
    val updatedAt: Long,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val chatId: String,
    val text: String,
    val updatedAt: Long,
)

@Dao
interface NanobotDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun sessions(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(rows: List<SessionEntity>)

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("SELECT * FROM thread_cache WHERE chatId = :chatId")
    suspend fun thread(chatId: String): ThreadCacheEntity?

    @Query("SELECT * FROM thread_cache")
    suspend fun threads(): List<ThreadCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(row: ThreadCacheEntity)

    @Query("DELETE FROM thread_cache WHERE chatId = :chatId")
    suspend fun deleteThread(chatId: String)

    @Query("SELECT * FROM drafts WHERE chatId = :chatId")
    suspend fun draft(chatId: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(row: DraftEntity)

    @Query("DELETE FROM drafts WHERE chatId = :chatId")
    suspend fun deleteDraft(chatId: String)
}

@Database(
    entities = [SessionEntity::class, ThreadCacheEntity::class, DraftEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NanobotDatabase : RoomDatabase() {
    abstract fun dao(): NanobotDao

    companion object {
        @Volatile
        private var instance: NanobotDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN projectPath TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN projectName TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN accessMode TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN restrictToWorkspace INTEGER")
            }
        }

        fun get(context: Context): NanobotDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NanobotDatabase::class.java,
                    "nanobot.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
