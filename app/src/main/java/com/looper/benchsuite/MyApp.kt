package com.looper.benchsuite

import android.content.Context
import com.looper.android.support.App

class MyApp : App() {

    companion object {
        fun applicationContext(): Context = App.applicationContext()
    }
}