package com.example.yamlist.data

import android.content.Context
import com.example.yamlist.R
import com.example.yamlist.data.local.entity.AppSettingEntity
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.data.repository.YamlistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts one sample project on first launch (spec §23) and records a flag so it
 * is never re-seeded.
 */
@Singleton
class SeedInitializer @Inject constructor(
    private val repo: YamlistRepository,
    // Application resources follow the device language, which is what a first
    // launch has (the in-app language override can't have been set yet).
    @ApplicationContext private val context: Context,
) {
    companion object { private const val SEED_KEY = "seeded_v1" }

    suspend fun seedIfFirstLaunch() {
        if (repo.settingDao.get(SEED_KEY) == "true") return
        // Guard against double-seed if the DB already has projects.
        if (repo.projects.getAllOnce().isNotEmpty()) {
            repo.settingDao.put(AppSettingEntity(SEED_KEY, "true"))
            return
        }
        val now = LocalDateTime.now()
        repo.withTransaction {
            val projectId = repo.projects.insert(
                ProjectEntity(
                    uuid = UUID.randomUUID().toString(),
                    title = context.getString(R.string.seed_project_title),
                    description = context.getString(R.string.seed_project_description),
                    progressMode = "BOTH",
                    displayOrder = 1,
                    createdAt = now, updatedAt = now,
                )
            )
            var order = 0L
            suspend fun addTask(title: String, parentId: Long?, weight: Double = 1.0): Long =
                repo.tasks.insert(
                    TaskEntity(
                        uuid = UUID.randomUUID().toString(),
                        projectId = projectId,
                        parentTaskId = parentId,
                        title = title,
                        weight = weight,
                        displayOrder = order++,
                        createdAt = now, updatedAt = now,
                    )
                )
            fun text(id: Int) = context.getString(id)
            val prep = addTask(text(R.string.seed_preparation), null)
            addTask(text(R.string.seed_drawing_check), prep, 2.0)
            addTask(text(R.string.seed_schedule_check), prep, 1.0)
            val onsite = addTask(text(R.string.seed_onsite_work), null)
            addTask(text(R.string.seed_panel_check), onsite, 2.0)
            addTask(text(R.string.seed_overall_test), onsite, 3.0)
            val closeout = addTask(text(R.string.seed_closeout), null)
            addTask(text(R.string.seed_report), closeout, 1.0)

            repo.settingDao.put(AppSettingEntity(SEED_KEY, "true"))
        }
    }
}
