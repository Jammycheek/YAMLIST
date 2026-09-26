package com.example.yamlist.data

import com.example.yamlist.data.local.entity.AppSettingEntity
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.data.repository.YamlistRepository
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
                    title = "サンプル現場 / Sample Site",
                    description = "初回サンプル / First-launch sample",
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
            val prep = addTask("事前準備 / Preparation", null)
            addTask("図面確認 / Drawing check", prep, 2.0)
            addTask("工程確認 / Schedule check", prep, 1.0)
            val onsite = addTask("現場作業 / On-site work", null)
            addTask("盤チェック / Panel check", onsite, 2.0)
            addTask("総合試験 / Overall test", onsite, 3.0)
            val closeout = addTask("完了処理 / Close-out", null)
            addTask("報告書作成 / Report", closeout, 1.0)

            repo.settingDao.put(AppSettingEntity(SEED_KEY, "true"))
        }
    }
}
