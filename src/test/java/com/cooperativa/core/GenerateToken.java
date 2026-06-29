import com.cooperativa.core.security.JwtUtil;

public class GenerateToken {
    public static void main(String[] args) {
        JwtUtil jwtUtil = new JwtUtil();
        // Just calling a test method or we can't because it's a Spring component?
        // It's just a simple class:
        // @Value("${jwt.secret:default-secret-key-very-long-for-hs256}")
        // String token = jwtUtil.generateToken("superadmin_frixon", "SUPER_ADMIN_SAAS", 1);
    }
}
