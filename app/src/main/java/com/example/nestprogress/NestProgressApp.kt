package com.example.nestprogress

import android.app.Application
import com.example.nestprogress.data.SeedInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NestProgressApp : Application() {

    @Inject lateinit var seedInitializer: SeedInitializer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedInitializer.seedIfFirstLaunch() }
    }
}
