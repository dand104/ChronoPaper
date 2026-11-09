package org.chronopaper.chronopaper

import android.app.Application
import org.chronopaper.chronopaper.di.AppContainer

class ChronoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.workScheduler.startObservingSettings()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ChronoApplication).container

private typealias Context = android.content.Context