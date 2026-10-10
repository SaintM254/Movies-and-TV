package com.vidsource.app

import android.app.Application
import com.vidsource.app.data.SourceRepository
import com.vidsource.app.util.FaviconStore

class VidsourceApp : Application() {

    lateinit var sources: SourceRepository
        private set

    lateinit var favicons: FaviconStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        sources = SourceRepository(this)
        favicons = FaviconStore(this)
    }

    companion object {
        lateinit var instance: VidsourceApp
            private set
    }
}
