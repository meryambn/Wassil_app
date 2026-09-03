import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
// Empty defaults so the app still builds before a Supabase project exists;
// SupabaseClient throws a clear error at runtime if these are blank.
val supabaseUrl = localProperties.getProperty("SUPABASE_URL") ?: ""
val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""

android {
    namespace = "com.example.wassilapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.wassilapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation ("com.google.android.gms:play-services-maps:18.2.0")
    implementation ("androidx.constraintlayout:constraintlayout:2.2.1")
    // -ndk27 variant: built with NDK 27 so the native libs are 16 KB page-size
    // aligned (required by Android 15+ devices and Google Play).
    implementation ("com.mapbox.maps:android-ndk27:11.16.6")
    implementation ("androidx.recyclerview:recyclerview:1.3.2")
    implementation ("androidx.cardview:cardview:1.0.0")

    // Supabase (REST over PostgREST/GoTrue) — plain Retrofit client since the
    // official supabase-kt SDK requires Kotlin and this app is Java.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // On-device text recognition for identity documents. Bundled rather than the
    // Play-Services-delivered variant so it works offline and on a first run, with no
    // model download and no API key. The model ships pre-trained: there is no WASSIL
    // data to train anything on, and would not be for a long time.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // The BOM pins every Firebase artifact to one compatible set, which is why the
    // messaging dependency below carries no version of its own.
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-messaging")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}