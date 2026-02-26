package info.isaksson.erland.modeller.server;

import info.isaksson.erland.modeller.server.domain.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RoleTest {

    @Test
    void orderingAtLeastWorks() {
        assertTrue(Role.OWNER.atLeast(Role.OWNER));
        assertTrue(Role.OWNER.atLeast(Role.EDITOR));
        assertTrue(Role.OWNER.atLeast(Role.VIEWER));

        assertFalse(Role.VIEWER.atLeast(Role.EDITOR));
        assertFalse(Role.EDITOR.atLeast(Role.OWNER));
        assertTrue(Role.EDITOR.atLeast(Role.VIEWER));
    }

    @Test
    void parseIsCaseInsensitiveAndTrims() {
        assertEquals(Role.OWNER, Role.parse("owner"));
        assertEquals(Role.EDITOR, Role.parse(" EDITOR "));
        assertEquals(Role.VIEWER, Role.parse("Viewer"));
        assertNull(Role.parse(null));
    }
}
