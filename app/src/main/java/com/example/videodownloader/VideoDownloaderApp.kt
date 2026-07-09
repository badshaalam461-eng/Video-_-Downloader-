package com.example.videodownloader

import android.app.Application
import com.example.videodownloader.extractor.OkHttpDownloaderImpl
import org.schabi.newpipe.extractor.NewPipe

class VideoDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpDownloaderImpl.getInstance())
    }
}
