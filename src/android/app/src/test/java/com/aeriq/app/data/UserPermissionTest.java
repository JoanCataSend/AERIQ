package com.aeriq.app.data;
/*
 * Fichero: UserPermissionTest.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/test/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class UserPermissionTest {
    private static final String COLLECT = AeriqAuthManager.User.PERMISSION_COLLECT;

    /**
     * Diseño lógico: serverPermissionsDecideEvenForNewRoles()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test

    /**
     * Diseño lógico: serverPermissionsDecideEvenForNewRoles()
     * Descripción: Procesa server permissions decide even for new roles y devuelve el resultado correspondiente..
     */
    public void serverPermissionsDecideEvenForNewRoles() {
        Set<String> granted = new HashSet<>(Arrays.asList(COLLECT, "mapa.organizacion"));
        assertTrue(AeriqAuthManager.User.can(true, granted, "tecnico_de_campo", COLLECT));
        assertFalse(AeriqAuthManager.User.can(true, granted, "tecnico_de_campo", "organizacion.gestionar"));
    }

    /**
     * Diseño lógico: emptyServerPermissionsDenyEvenForOldRoleNames()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test

    /**
     * Diseño lógico: emptyServerPermissionsDenyEvenForOldRoleNames()
     * Descripción: Procesa empty server permissions deny even for old role names y devuelve el resultado correspondiente..
     */
    public void emptyServerPermissionsDenyEvenForOldRoleNames() {
        assertFalse(AeriqAuthManager.User.can(true, Collections.emptySet(), "trabajador", COLLECT));
    }

    /**
     * Diseño lógico: olderServerFallsBackToRoleCodes()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test

    /**
     * Diseño lógico: olderServerFallsBackToRoleCodes()
     * Descripción: Procesa older server falls back to role codes y devuelve el resultado correspondiente..
     */
    public void olderServerFallsBackToRoleCodes() {
        assertTrue(AeriqAuthManager.User.can(false, Collections.emptySet(), "trabajador", COLLECT));
        assertTrue(AeriqAuthManager.User.can(false, Collections.emptySet(), "admin", COLLECT));
        assertFalse(AeriqAuthManager.User.can(false, Collections.emptySet(), "analista", COLLECT));
        assertFalse(AeriqAuthManager.User.can(false, Collections.emptySet(), "admin", "organizacion.gestionar"));
    }
}
