package com.aeriq.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PasswordRulesTest {
    /**
     * Prueba el comportamiento de acceptsAStrongPassword con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → acceptsAStrongPassword() → void; según doc/android_design.md.
     */
    @Test public void acceptsAStrongPassword() {
        assertEquals(Collections.emptyList(), PasswordRules.missing("Ozono#2026"));
        assertEquals(Collections.emptyList(), PasswordRules.missing("Año-ñu9Z"));
        assertEquals("", PasswordRules.problem("Ozono#2026"));
    }

    /**
     * Prueba el comportamiento de rejectsShortPasswords con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → rejectsShortPasswords() → void; según doc/android_design.md.
     */
    @Test public void rejectsShortPasswords() {
        assertEquals(Collections.singletonList(PasswordRules.LENGTH), PasswordRules.missing("Oz#2026"));
    }

    /**
     * Prueba el comportamiento de reportsEachMissingRequirement con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → reportsEachMissingRequirement() → void; según doc/android_design.md.
     */
    @Test public void reportsEachMissingRequirement() {
        assertEquals(Arrays.asList(PasswordRules.UPPER, PasswordRules.SYMBOL), PasswordRules.missing("ozono2026"));
        assertEquals(Arrays.asList(PasswordRules.LOWER, PasswordRules.DIGIT), PasswordRules.missing("OZONO#ABC"));
        assertEquals(PasswordRules.ALL, PasswordRules.missing(""));
        assertTrue(PasswordRules.problem("ozono2026").endsWith("Le falta: una mayúscula y un símbolo (por ejemplo ! ? # @ . -)."));
    }

    /**
     * Prueba el comportamiento de spacesAreNotSymbols con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → spacesAreNotSymbols() → void; según doc/android_design.md.
     */
    @Test public void spacesAreNotSymbols() {
        assertEquals(Collections.singletonList(PasswordRules.SYMBOL), PasswordRules.missing("Ozono 2026"));
    }
}
