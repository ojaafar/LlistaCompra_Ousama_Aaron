plugins {
    alias(libs.plugins.android.application)

    // Procesa la configuración de google-services.json.
    alias(libs.plugins.google.services)
}

android {
    namespace = "org.insbaixcamp.llistacompra_ousama_aaron"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.insbaixcamp.llistacompra_ousama_aaron"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Coordina las versiones de las bibliotecas de Firebase.
    implementation(platform(libs.firebase.bom))

    // Registro e inicio de sesión.
    implementation(libs.firebase.auth)

    // Base de datos para listas, categorías y productos.
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}