package com.aeriq.app;
/*
 * Fichero: ExampleInstrumentedTest.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/androidTest/java/com/aeriq/app.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;

@RunWith(AndroidJUnit4.class)
public final class ExampleInstrumentedTest {
    /**
     * Diseño lógico: applicationId()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void applicationId() {
        assertEquals("com.aeriq.app", InstrumentationRegistry.getInstrumentation()
            .getTargetContext().getPackageName());
    }

    /**
     * Diseño lógico: opensPublicMapAndKeepsAccountBehindLogin()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void opensPublicMapAndKeepsAccountBehindLogin() {

      /**
       * Diseño lógico: scenario: Text --> try() --> Text
       * Descripción: Procesa try y devuelve el resultado correspondiente.
       */
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("Iniciar sesión")).check(matches(isDisplayed()));
            onView(withText("Crear cuenta")).check(doesNotExist());
            onView(withContentDescription("Mostrar u ocultar contraseña")).perform(click());
            onView(withContentDescription("Mostrar u ocultar contraseña")).perform(click());
            onView(withContentDescription("Mostrar u ocultar contraseña")).perform(click());
            onView(withText("Abrir mapa")).perform(scrollTo(), click());
            onView(withText("Mapa de ozono")).check(matches(isDisplayed()));
            onView(withText("Cuenta")).perform(click());
            onView(withText("Iniciar sesión")).check(matches(isDisplayed()));
        }
    }
}
