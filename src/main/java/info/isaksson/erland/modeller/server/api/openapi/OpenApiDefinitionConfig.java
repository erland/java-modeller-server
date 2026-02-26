package info.isaksson.erland.modeller.server.api.openapi;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;

/**
 * Central OpenAPI metadata for the server.
 *
 * Endpoint schemas are derived from JAX-RS resources and DTOs.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Java Modeller Server API",
                version = "1",
                description = "Phase 1 Central Modeling Server API (datasets + snapshots).",
                contact = @Contact(name = "Erland Isaksson"),
                license = @License(name = "MIT")
        )
)
public class OpenApiDefinitionConfig {
}
