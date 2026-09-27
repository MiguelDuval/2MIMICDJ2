package com.miguenduval.mimicdj2

import android.app.Application
import timber.log.Timber

class MimicDjApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}