package info.isaksson.erland.modeller.server;

import info.isaksson.erland.modeller.server.domain.Role;
import info.isaksson.erland.modeller.server.security.DatasetAuthorizationService;
import info.isaksson.erland.modeller.server.persistence.repositories.DatasetAclRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

public class DatasetAuthorizationServiceTest {

    @Test
    void unauthenticatedThrowsNotAuthorized() {
        DatasetAclRepository repo = Mockito.mock(DatasetAclRepository.class);
        SecurityIdentity identity = Mockito.mock(SecurityIdentity.class);
        Mockito.when(identity.isAnonymous()).thenReturn(true);

        DatasetAuthorizationService svc = new DatasetAuthorizationService(repo, identity);

        assertThrows(NotAuthorizedException.class, () -> svc.requireViewer(UUID.randomUUID()));
    }

    @Test
    void insufficientRoleThrowsForbidden() {
        DatasetAclRepository repo = Mockito.mock(DatasetAclRepository.class);
        SecurityIdentity identity = Mockito.mock(SecurityIdentity.class);

        UUID datasetId = UUID.randomUUID();

        Mockito.when(identity.isAnonymous()).thenReturn(false);
        Principal principal = () -> "sub-1";
        Mockito.when(identity.getPrincipal()).thenReturn(principal);

        Mockito.when(repo.findRole(eq(datasetId), eq("sub-1"))).thenReturn(Optional.of(Role.VIEWER));

        DatasetAuthorizationService svc = new DatasetAuthorizationService(repo, identity);

        assertThrows(ForbiddenException.class, () -> svc.requireEditor(datasetId));
    }

    @Test
    void sufficientRoleAllows() {
        DatasetAclRepository repo = Mockito.mock(DatasetAclRepository.class);
        SecurityIdentity identity = Mockito.mock(SecurityIdentity.class);

        UUID datasetId = UUID.randomUUID();

        Mockito.when(identity.isAnonymous()).thenReturn(false);
        Principal principal = () -> "sub-1";
        Mockito.when(identity.getPrincipal()).thenReturn(principal);

        Mockito.when(repo.findRole(eq(datasetId), eq("sub-1"))).thenReturn(Optional.of(Role.OWNER));

        DatasetAuthorizationService svc = new DatasetAuthorizationService(repo, identity);

        assertDoesNotThrow(() -> svc.requireEditor(datasetId));
        assertDoesNotThrow(() -> svc.requireOwner(datasetId));
    }
}
