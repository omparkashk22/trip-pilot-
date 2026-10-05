import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
  versionPropsFile.inputStream().use { versionProps.load(it) }
}

val envBuildNumber = System.getenv("BUILD_NUMBER") ?: System.getenv("GITHUB_RUN_NUMBER")
val buildNumberVal: Int = envBuildNumber?.toIntOrNull()
  ?: versionProps.getProperty("buildNumber", "1").toIntOrNull()
  ?: 1

gradle.taskGraph.whenReady {
  val isReleaseTask = allTasks.any { it.name.contains("Release", ignoreCase = true) }
  if (isReleaseTask && envBuildNumber == null && versionPropsFile.exists()) {
    val nextBuild = buildNumberVal + 1
    versionProps.setProperty("buildNumber", nextBuild.toString())
    versionPropsFile.outputStream().use {
      versionProps.store(it, "Updated by release build")
    }
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.trippilot.vqxrt"
    minSdk = 24
    targetSdk = 36
    versionCode = buildNumberVal
    versionName = "1.0.$buildNumberVal"

    buildConfigField("String", "DEFAULT_UPDATE_SOURCE", "\"GITHUB\"")
    buildConfigField("String", "GITHUB_OWNER", "\"omparkashk22\"")
    buildConfigField("String", "GITHUB_REPO", "\"trip-pilot-\"")
    buildConfigField("String", "HOSTED_VERSION_JSON_URL", "\"https://raw.githubusercontent.com/omparkashk22/trip-pilot-/main/version.json\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val localPropsFile = rootProject.file("local.properties")
      val localProps = Properties()
      if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { localProps.load(it) }
      }
      val keystorePath = System.getenv("KEYSTORE_PATH")
        ?: localProps.getProperty("KEYSTORE_PATH")
        ?: "${rootDir}/my-upload-key.jks"
      val storePasswordVal = System.getenv("STORE_PASSWORD")
        ?: localProps.getProperty("STORE_PASSWORD")
      val keyAliasVal = System.getenv("KEY_ALIAS")
        ?: localProps.getProperty("KEY_ALIAS")
        ?: "upload"
      val keyPasswordVal = System.getenv("KEY_PASSWORD")
        ?: localProps.getProperty("KEY_PASSWORD")

      val keyFile = file(keystorePath)
      if (keyFile.exists() && !storePasswordVal.isNullOrBlank()) {
        storeFile = keyFile
        storePassword = storePasswordVal
        keyAlias = keyAliasVal
        keyPassword = keyPasswordVal
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Firestore:
  implementation(libs.firebase.firestore)

  // Firebase Auth:
  implementation(libs.firebase.auth)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
