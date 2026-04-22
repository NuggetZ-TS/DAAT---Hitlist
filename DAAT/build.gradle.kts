// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
<<<<<<< HEAD
    id("com.google.gms.google-services") version "4.4.4" apply false
}
=======
    alias(libs.plugins.google.services) apply false
}
>>>>>>> 1e1e69af1b199ca3f1eee03c2522ddf5b15b689a
