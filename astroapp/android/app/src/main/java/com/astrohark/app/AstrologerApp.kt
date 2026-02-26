package com.astrohark.app

import android.app.Application

class AstrologerApp : Application() {
    override fun onCreate() {
        super.onCreate()
         com.astrohark.app.data.remote.SocketManager.init()
    }
}
