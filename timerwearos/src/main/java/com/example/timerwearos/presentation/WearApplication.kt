package com.example.timerwearos.presentation

import android.app.Application
import com.google.firebase.FirebaseApp

class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}