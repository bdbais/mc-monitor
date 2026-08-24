import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/*
 * Il progetto vive dentro una cartella sincronizzata su Google Drive. Il client
 * tiene aperti i file mentre li copia, e Gradle non riesce più a cancellare le
 * cartelle intermedie: il merge delle risorse fallisce a metà. Lo stesso difetto
 * ha già prodotto APK troncati, caricati mentre la sincronizzazione era in corso.
 *
 * Le cartelle di lavoro vanno quindi su disco locale. In CI, dove il percorso di
 * Drive non esiste, resta il default e i workflow non cambiano.
 */
val onCloudFolder = rootDir.invariantSeparatorsPath.contains("/googledrive/", ignoreCase = true)
if (onCloudFolder) {
    layout.buildDirectory.set(File(System.getProperty("java.io.tmpdir"), "mcmonitor-build/app"))
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.bellizia.mcmonitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bellizia.mcmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 36
        versionName = "1.33"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")

                // v1 non serve con minSdk 26 e lascerebbe nell'archivio file di
                // firma inutilizzati: meglio v2 e v3 puliti.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    /*
     * "diagnostica" è una copia dell'app senza i componenti aggiunti dopo la 1.7:
     * servizio in primo piano, ricevitore all'avvio, FileProvider e permesso di
     * installare pacchetti. Ha un identificativo diverso, quindi convive con
     * l'app normale e serve solo a capire quale componente un telefono rifiuta.
     */
    flavorDimensions += "tipo"
    productFlavors {
        create("normale") {
            dimension = "tipo"
            isDefault = true
        }
        create("diagnostica") {
            dimension = "tipo"
            applicationIdSuffix = ".diag"
            versionNameSuffix = "-diag"
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // Per scrivere nella cartella scelta con il selettore di sistema (cloud o locale)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // Sblocco con impronta o volto davanti alla password dell'app
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Fork mantenuto di JSch, con supporto agli algoritmi moderni di OpenSSH
    implementation("com.github.mwiede:jsch:0.2.17")

    testImplementation("junit:junit:4.13.2")
    // Implementazione vera di org.json: nei test unitari android.jar ha solo stub.
    testImplementation("org.json:json:20240303")
}
