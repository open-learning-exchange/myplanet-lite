import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

val mockitoAgent by configurations.creating {
    isTransitive = false
}
val mockkAgent: Configuration by configurations.creating

android {
    namespace = "org.ole.planet.myplanet.lite"
    compileSdk = 36
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        applicationId = "org.ole.planet.myplanet.lite"
        minSdk = 28
        targetSdk = 36
        versionCode = 30
        versionName = "0.0.30"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PLANET_BASE_URL", "\"http://10.82.1.30/\"")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.logging", "none")
    systemProperty("robolectric.logging.enabled", "false")

    doFirst {
        val agentFile = mockkAgent.find { it.name.startsWith("byte-buddy-agent") }
        if (agentFile != null) {
            jvmArgs("-javaagent:$agentFile")
        }
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
            jvmArgs("-XX:+EnableDynamicAgentLoading")
        }
        jvmArgs("-javaagent:${mockitoAgent.singleFile}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    mockkAgent(libs.byte.buddy)
    mockkAgent(libs.byte.buddy.agent)
    mockitoAgent(libs.mockito.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.logging.interceptor)
    implementation(libs.core)
    implementation(libs.photoview)
    implementation(libs.worldcountrydata)
    implementation(libs.ucrop)
    implementation(libs.circleimageview)
    implementation(libs.glide)
    implementation(libs.language.id)
    implementation(libs.ext.tables)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.core)
    testImplementation(libs.core.ktx)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.core.ktx)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
