package com.example.yamlist.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.yamlist.data.local.dao.AppSettingDao
import com.example.yamlist.data.local.dao.ProjectDao
import com.example.yamlist.data.local.dao.TaskCommentDao
import com.example.yamlist.data.local.dao.TaskDao
import com.example.yamlist.data.local.entity.AppSettingEntity
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskCommentEntity
import com.example.yamlist.data.local.entity.TaskEntity

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        TaskCommentEntity::class,
        AppSettingEntity::class,
    ],
    // v2: added TaskCommentEntity.struck (v0.4 comment strikethrough).
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YamlistDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun taskCommentDao(): TaskCommentDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val NAME = "yamlist.db"
    }
}
