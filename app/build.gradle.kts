plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.jodel.videopocket"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jodel.videopocket"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    testImplementation("junit:junit:4.13.2")
}

val prepareDownloadEngine by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/prepare_engine.py")
    inputs.file(rootProject.file("engine.json"))
    inputs.file(rootProject.file("scripts/prepare_engine.py"))
    outputs.file(project.file("src/main/res/raw/ytdlp"))
    outputs.file(project.file("src/main/assets/engine-version.txt"))
}
tasks.named("preBuild") { dependsOn(prepareDownloadEngine) }
