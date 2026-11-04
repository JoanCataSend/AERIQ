package com.aeriq.app;

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

@RunWith(AndroidJUnit4.class)
public final class ExampleInstrumentedTest {
    @Test public void applicationId() {
        assertEquals("com.aeriq.app", InstrumentationRegistry.getInstrumentation()
            .getTargetContext().getPackageName());
    }

    @Test public void opensPublicMapAndKeepsAccountBehindLogin() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("Iniciar sesión")).check(matches(isDisplayed()));
            onView(withText("Crear cuenta")).check(doesNotExist());
            onView(withText("Mostrar")).perform(click());
            onView(withText("Ocultar")).check(matches(isDisplayed())).perform(click());
            onView(withText("Mostrar")).check(matches(isDisplayed()));
            onView(withText("Abrir mapa")).perform(scrollTo(), click());
            onView(withText("Mapa de ozono")).check(matches(isDisplayed()));
            onView(withText("Cuenta")).perform(click());
            onView(withText("Iniciar sesión")).check(matches(isDisplayed()));
        }
    }
}
