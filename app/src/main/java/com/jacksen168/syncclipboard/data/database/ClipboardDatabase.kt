package com.jacksen168.syncclipboard.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jacksen168.syncclipboard.data.model.ClipboardItem
import com.jacksen168.syncclipboard.data.model.ClipboardType
import com.jacksen168.syncclipboard.data.model.ClipboardSource
import java.util.Date

/**
 * 剪贴板项目DAO
 */
@Dao
interface ClipboardItemDao {
    
    @Query("SELECT * FROM clipboard_items ORDER BY timestamp DESC")
    suspend fun getAllItems(): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentItems(limit: Int = 50): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items WHERE id = :id")
    suspend fun getItemById(id: String): ClipboardItem?
    
    @Query("SELECT * FROM clipboard_items WHERE isSynced = 0 ORDER BY timestamp DESC")
    suspend fun getUnsyncedItems(): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items WHERE contentHash = :contentHash")
    suspend fun getItemsByContentHash(contentHash: String): List<ClipboardItem>
    
    @Query("SELECT * FROM clipboard_items WHERE type = 'IMAGE' ORDER BY timestamp DESC")
    suspend fun getImageItems(): List<ClipboardItem>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ClipboardItem)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ClipboardItem>)
    
    @Update
    suspend fun updateItem(item: ClipboardItem)
    
    @Query("UPDATE clipboard_items SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
    
    @Delete
    suspend fun deleteItem(item: ClipboardItem)
    
    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteItemById(id: String)
    
    @Query("DELETE FROM clipboard_items WHERE timestamp < :timestamp")
    suspend fun deleteOldItems(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM clipboard_items")
    suspend fun getItemCount(): Int
    
    @Query("DELETE FROM clipboard_items WHERE id IN (SELECT id FROM clipboard_items ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestItems(count: Int)
    
    @Query("SELECT * FROM clipboard_items ORDER BY timestamp ASC LIMIT :count")
    suspend fun getOldestItems(count: Int): List<ClipboardItem>
}

/**
 * Room数据库类
 */
@Database(
    entities = [ClipboardItem::class],
    version = 2, // 增加版本号
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ClipboardDatabase : RoomDatabase() {
    
    abstract fun clipboardItemDao(): ClipboardItemDao
    
    companion object {
        const val DATABASE_NAME = "clipboard_database"
        
        // 数据库迁移：从版本1到版本2（添加新字段）
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段
                database.execSQL("ALTER TABLE clipboard_items ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'")
                database.execSQL("ALTER TABLE clipboard_items ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clipboard_items ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE clipboard_items ADD COLUMN isSyncing INTEGER NOT NULL DEFAULT 0")
                
                // 为现有记录生成内容哈希
                database.execSQL("""
                    UPDATE clipboard_items 
                    SET contentHash = SUBSTR(HEX(RANDOMBLOB(16)), 1, 32),
                        lastModified = timestamp
                    WHERE contentHash = ''
                """)
            }
        }
    }
}

/**
 * Room类型转换器
 */
class Converters {
    
    @TypeConverter
    fun fromClipboardType(type: ClipboardType): String {
        return type.name
    }
    
    @TypeConverter
    fun toClipboardType(type: String): ClipboardType {
        return ClipboardType.valueOf(type)
    }
    
    @TypeConverter
    fun fromClipboardSource(source: ClipboardSource): String {
        return source.name
    }
    
    @TypeConverter
    fun toClipboardSource(source: String): ClipboardSource {
        return ClipboardSource.valueOf(source)
    }
    
    @TypeConverter
    fun fromDate(date: Date): Long {
        return date.time
    }
    
    @TypeConverter
    fun toDate(timestamp: Long): Date {
        return Date(timestamp)
    }
}