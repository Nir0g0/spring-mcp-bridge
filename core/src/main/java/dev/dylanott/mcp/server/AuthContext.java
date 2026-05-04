package dev.dylanott.mcp.server;

import java.util.Collections;
import java.util.Set;

public record AuthContext(String principal, Set<String> roles, boolean authenticated) {

    public static final AuthContext ANONYMOUS = new AuthContext(null, Collections.emptySet(), false);

    public static AuthContext of(String principal, Set<String> roles) {
        return new AuthContext(principal, Set.copyOf(roles), true);
    }

    public boolean hasAnyRole(String[] required) {
        if (required == null || required.length == 0) {
            return true;
        }
        for (String role : required) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
