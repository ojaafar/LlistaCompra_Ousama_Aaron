// Configuración común del proyecto.
plugins {
    alias(libs.plugins.android.application) apply false

    // Declara el complemento de Google Services.
    alias(libs.plugins.google.services) apply false
}