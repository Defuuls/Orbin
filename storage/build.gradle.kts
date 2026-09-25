plugins {
    alias(libs.plugins.orbin.kmp.room)
}

// What the reader keeps, shared with iOS: the Room database (entities, DAOs and the repositories
// built only on them) and the board preferences kept in DataStore. No DI annotations — `data` provides these to Android's Hilt graph, `:app-ios` builds them
// itself. How the database is opened stays per platform (see OrbinDatabase).
kotlin {
    android {
        namespace = "com.orbin.storage"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            implementation(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.immutable)
            api(libs.androidx.datastore.preferences.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // The database-backed tests run on the iOS simulator, over the same bundled SQLite driver
        // the iOS app opens the database with. Android's side is tested in `data`.
        iosTest.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
