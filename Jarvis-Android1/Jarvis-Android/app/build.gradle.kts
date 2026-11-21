plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.jarvis.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.jarvis.app"; minSdk = 26; targetSdk = 34; versionCode = 2; versionName = "2.0"
        // Entered ONCE as GitHub secrets, baked into the build:
        buildConfigField("String", "JARVIS_URL", q(System.getenv("JARVIS_URL") ?: ""))
        buildConfigField("String", "JARVIS_KEY", q(System.getenv("JARVIS_KEY") ?: ""))
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
