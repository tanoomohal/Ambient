package com.tanoo.ambient

import android.app.Application

class AmbientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    companion object {
        lateinit var prefs: Prefs
            private set
    }
}
