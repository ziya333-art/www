plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// KSP must be applied before Hilt so Hilt can find KSP's task class at config time.
// Both come from the root buildscript classpath (shared, no classloader isolation).
apply(plugin = "com.google.devtools.ksp")
apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "com.jegly.www"
    compileSdk = 37

    defaultConfig {
        /*
         * Briefly set to a generic id (org.chromium.webview) while the X-Requested-With header
         * could only be blanked on top-level navigations — the real package name was reaching every
         * third-party host a page touched, so hiding it in the id was the only lever available.
         *
         * No longer necessary: applyProfileWideBlankedRequestedWith() in BrowserWebView now blanks
         * that header for every request through Profile#addCustomHeader, verified empty on
         * WebView 149. The id doesn't leak, so there is nothing to obscure.
         *
         * Residual exposure, if anyone reconsiders this: WebSocket requests are outside that API's
         * scope, and a WebView provider too old for CUSTOM_REQUEST_HEADERS falls back to
         * top-level-only blanking. Both would carry this value.
         */
        applicationId = "com.jegly.www"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // buildConfig is off by default in AGP 8+; the About section reads BuildConfig.VERSION_NAME so
    // the version shown in the app can never drift from the one declared above.
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            // Adopt the future Kotlin default: annotations with no explicit target apply to
            // both the constructor parameter and the backing property (KT-73255).
            "-Xannotation-default-target=param-property"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.browser:browser:1.10.0")
    // Feature-detected WebView APIs: algorithmic darkening (force-dark web content) and the
    // ProxyController that an Orbot/Tor option would need later.
    implementation("androidx.webkit:webkit:1.16.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.19.0")

    // Room & SQLCipher
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    "ksp"("androidx.room:room-compiler:2.8.4")
    implementation("net.zetetic:sqlcipher-android:4.14.1@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.6.2")

    // Networking. Retrofit is gone with the feed API; OkHttp stays because the DoH resolver,
    // private-network guard, and Coil's image fetcher are all built on it.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    "ksp"("com.google.dagger:hilt-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")

    // Compose image loader, for favicons. Routed through our hardened OkHttp (DoH, HTTPS, size caps).
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
}
