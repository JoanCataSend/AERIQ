package com.aeriq.app.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class UserPermissionTest {
    private static final String COLLECT = AeriqAuthManager.User.PERMISSION_COLLECT;

    @Test
    public void serverPermissionsDecideEvenForNewRoles() {
        Set<String> granted = new HashSet<>(Arrays.asList(COLLECT, "mapa.organizacion"));
        assertTrue(AeriqAuthManager.User.can(true, granted, "tecnico_de_campo", COLLECT));
        assertFalse(AeriqAuthManager.User.can(true, granted, "tecnico_de_campo", "organizacion.gestionar"));
    }

    @Test
    public void emptyServerPermissionsDenyEvenForOldRoleNames() {
        assertFalse(AeriqAuthManager.User.can(true, Collections.emptySet(), "trabajador", COLLECT));
    }

    @Test
    public void olderServerFallsBackToRoleCodes() {
        assertTrue(AeriqAuthManager.User.can(false, Collections.emptySet(), "trabajador", COLLECT));
        assertTrue(AeriqAuthManager.User.can(false, Collections.emptySet(), "admin", COLLECT));
        assertFalse(AeriqAuthManager.User.can(false, Collections.emptySet(), "analista", COLLECT));
        assertFalse(AeriqAuthManager.User.can(false, Collections.emptySet(), "admin", "organizacion.gestionar"));
    }
}
