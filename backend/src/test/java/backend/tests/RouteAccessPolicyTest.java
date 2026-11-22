package backend.tests;

import backend.router.RouteAccessPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAccessPolicyTest {

    @Test
    void allowsOnlyExplicitPublicRoutes() {
        assertTrue(RouteAccessPolicy.isPublic("GET", "/health"));
        assertTrue(RouteAccessPolicy.isPublic("POST", "/auth/register"));
        assertTrue(RouteAccessPolicy.isPublic("post", "/auth/login"));
        assertTrue(RouteAccessPolicy.isPublic("GET", "/"));
        assertTrue(RouteAccessPolicy.isPublic("GET", "/favicon.svg"));
        assertTrue(RouteAccessPolicy.isPublic("GET", "/assets/index-abc.js"));
        assertFalse(RouteAccessPolicy.requiresAuthentication("GET", "/health"));
    }

    @Test
    void requiresAuthenticationForPrivateAndFutureRoutes() {
        for (String path : new String[] {
            "/medications", "/auth/me", "/auth/logout", "/treatments", "/new-route"
        }) {
            assertTrue(RouteAccessPolicy.requiresAuthentication("GET", path));
            assertTrue(RouteAccessPolicy.requiresAuthentication("POST", path));
        }
    }

    @Test
    void doesNotAllowOtherMethodsOrSimilarPaths() {
        assertTrue(RouteAccessPolicy.requiresAuthentication("POST", "/health"));
        assertTrue(RouteAccessPolicy.requiresAuthentication("GET", "/auth/login"));
        assertTrue(RouteAccessPolicy.requiresAuthentication("GET", "/auth/register"));
        assertTrue(RouteAccessPolicy.requiresAuthentication("GET", "/health/private"));
        assertTrue(RouteAccessPolicy.requiresAuthentication("POST", "/auth/login/"));
        assertTrue(RouteAccessPolicy.requiresAuthentication("POST", "/assets/index-abc.js"));
    }

    @Test
    void doesNotAllowMissingMethodOrPath() {
        assertTrue(RouteAccessPolicy.requiresAuthentication(null, "/health"));
        assertTrue(RouteAccessPolicy.requiresAuthentication("GET", null));
        assertTrue(RouteAccessPolicy.requiresAuthentication(null, null));
    }
}
