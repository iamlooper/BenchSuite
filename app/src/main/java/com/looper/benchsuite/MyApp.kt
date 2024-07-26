package com.looper.benchsuite

import android.content.Context
import com.looper.android.support.App

class MyApp : App() {

    companion object {
        fun getAppContext(): Context? = App.getAppContext()
    }

    override fun onCreate() {
        super.onCreate()
    }
}