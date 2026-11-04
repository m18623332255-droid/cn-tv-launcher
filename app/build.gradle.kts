plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
  namespace = "com.cn.tvlauncher"; compileSdk = 34
  defaultConfig { applicationId = "com.cn.tvlauncher"; minSdk = 23; targetSdk = 34; versionCode = 4; versionName = "1.0.3"; multiDexEnabled = true }
  buildFeatures { compose = true; buildConfig = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
  kotlinOptions { jvmTarget = "17" }
  packaging { resources { excludes += setOf("META-INF/*.kotlin_module","META-INF/LICENSE*","META-INF/NOTICE*","META-INF/versions/9/OSGI-INF/MANIFEST.MF") } }
  testOptions { unitTests.isReturnDefaultValues = true }
  lint { abortOnError = false; checkReleaseBuilds = false }
}
dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
  implementation(platform("androidx.compose:compose-bom:2024.06.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.activity:activity-compose:1.9.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
  implementation("io.coil-kt:coil-compose:2.6.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20231013")
}
