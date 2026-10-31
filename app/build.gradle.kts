plugins {
    id("com.android.application")
}

android {
    namespace = "com.netbridge.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.netbridge.vpn"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}


dependencies {
    implementation("com.github.singbox-android:libbox:1.14.0")
}
