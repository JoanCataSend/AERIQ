# Fichero: settings.gradle.kts
# Autor: Codigo Norte
# Descripción: Archivo fuente de src/android.
# Fecha: 2026-09-25
# Copyright (c) 2026 Codigo Norte
# Autor: Codigo Norte
# Descripción: Archivo fuente de src/android.
# Fecha: 2026-09-25
# Copyright (c) 2026 Codigo Norte
# Autor: Codigo Norte
# Descripción: Archivo fuente de src/android.
# Fecha: 2026-09-25
# Copyright (c) 2026 Codigo Norte
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AERIQ"
include(":app")
 