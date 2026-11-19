package com.example

import android.app.Application
import com.example.data.local.PreferencesRepository
import com.example.data.local.SonicBloomDatabase
import com.example.data.repository.MusicLibraryRepository
import com.example.playback.AudioEffectManager
import com.example.playback.PlaybackController

class AppContainer(application: Application) {
    val database: SonicBloomDatabase = SonicBloomDatabase.getInstance(application)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(application)
    val musicLibraryRepository: MusicLibraryRepository = MusicLibraryRepository(
        context = application,
        dao = database.sonicBloomDao()
    )
    val audioEffectManager: AudioEffectManager = AudioEffectManager()
    val playbackController: PlaybackController = PlaybackController(
        context = application,
        libraryRepository = musicLibraryRepository,
        preferencesRepository = preferencesRepository
    )
}

class SonicBloomApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
