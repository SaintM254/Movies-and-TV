# Keep the JS bridge used by the video URL extractor.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.vidsource.app.ui.browser.** { *; }

# Media3 / ExoPlayer ships its own consumer rules; nothing extra required here.
