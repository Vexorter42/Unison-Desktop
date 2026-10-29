pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // NewPipe Extractor (YouTube, SoundCloud) публикуется через JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "Unison-Desktop"
