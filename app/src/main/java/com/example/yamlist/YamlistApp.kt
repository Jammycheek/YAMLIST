package com.example.yamlist

import android.app.Application
import com.example.yamlist.data.SeedInitializer
import com.example.yamlist.data.repository.YamlistRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class YamlistApp : Application() {

    @Inject lateinit var seedInitializer: SeedInitializer
    @Inject lateinit var repo: YamlistRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repo.normalizeLegacyTaskStatus()
            seedInitializer.seedIfFirstLaunch()
        }
    }
}
