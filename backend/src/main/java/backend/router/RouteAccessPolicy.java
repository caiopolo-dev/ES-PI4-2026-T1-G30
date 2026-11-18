package backend.router;

public final class RouteAccessPolicy {

    private RouteAccessPolicy() {
    }

    // Apenas estas combinações de método e caminho permitem acesso público.
    public static boolean isPublic(String method, String path) {
        return ("GET".equalsIgnoreCase(method) && "/health".equals(path))
            || ("POST".equalsIgnoreCase(method) && "/auth/register".equals(path))
            || ("POST".equalsIgnoreCase(method) && "/auth/login".equals(path));
    }

    // Qualquer outra rota exige autenticação por padrão.
    public static boolean requiresAuthentication(String method, String path) {
        return !isPublic(method, path);
    }
}
