package com.aeriq.app.data;
/*
 * Fichero: PasswordRulesTest.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/test/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PasswordRulesTest {
    /**
     * Diseño lógico: acceptsAStrongPassword()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void acceptsAStrongPassword() {
        assertEquals(Collections.emptyList(), PasswordRules.missing("Ozono#2026"));
        assertEquals(Collections.emptyList(), PasswordRules.missing("Año-ñu9Z"));
        assertEquals("", PasswordRules.problem("Ozono#2026"));
    }

    /**
     * Diseño lógico: rejectsShortPasswords()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void rejectsShortPasswords() {
        assertEquals(Collections.singletonList(PasswordRules.LENGTH), PasswordRules.missing("Oz#2026"));
    }

    /**
     * Diseño lógico: reportsEachMissingRequirement()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void reportsEachMissingRequirement() {
        assertEquals(Arrays.asList(PasswordRules.UPPER, PasswordRules.SYMBOL), PasswordRules.missing("ozono2026"));
        assertEquals(Arrays.asList(PasswordRules.LOWER, PasswordRules.DIGIT), PasswordRules.missing("OZONO#ABC"));
        assertEquals(PasswordRules.ALL, PasswordRules.missing(""));
        assertTrue(PasswordRules.problem("ozono2026").endsWith("Le falta: una mayúscula y un símbolo (por ejemplo ! ? # @ . -)."));
    }

    /**
     * Diseño lógico: spacesAreNotSymbols()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void spacesAreNotSymbols() {
        assertEquals(Collections.singletonList(PasswordRules.SYMBOL), PasswordRules.missing("Ozono 2026"));
    }
}
