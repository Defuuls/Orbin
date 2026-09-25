package com.orbin.uinext

import android.app.Application
import android.content.ContentProvider
import org.robolectric.Robolectric

/**
 * The screens read their strings through Compose Multiplatform resources, which find the Android
 * context through a content provider that the app's merged manifest starts at launch. Robolectric
 * does not start content providers, so every test would fail on the first string. Starting it here
 * is exactly what a real launch does. Wired up for every test in `robolectric.properties`.
 *
 * The provider is Kotlin-internal, so it is named the way the library's own manifest names it.
 */
class ComposeResourcesTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val provider = Class.forName(RESOURCES_CONTEXT_PROVIDER).asSubclass(ContentProvider::class.java)
        Robolectric.setupContentProvider(provider)
    }

    private companion object {
        const val RESOURCES_CONTEXT_PROVIDER = "org.jetbrains.compose.resources.AndroidContextProvider"
    }
}
