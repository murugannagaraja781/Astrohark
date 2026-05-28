package com.astrohark.app

import android.app.Application

class AstrologerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Facebook SDK for App Events
        com.facebook.FacebookSdk.sdkInitialize(applicationContext)
        com.facebook.appevents.AppEventsLogger.activateApp(this)

        com.astrohark.app.data.remote.SocketManager.init()
    }
}
