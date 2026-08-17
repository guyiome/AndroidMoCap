package com.guyiome.androidmocap

import android.app.Application
import com.guyiome.androidmocap.logging.AppLog

class MoCapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Au plus tôt, avant tout log applicatif -- voir kdoc d'AppLog.
        // Le niveau de persistance (AppSettingsStore.logLevel) est synchronisé séparément depuis
        // MainViewModel.init{}, même patron que les autres réglages DataStore collectés là-bas.
        AppLog.init(filesDir)
    }
}
