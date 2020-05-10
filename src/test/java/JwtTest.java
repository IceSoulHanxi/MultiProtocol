import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.Test;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/4/8 16:10
 */
public class JwtTest {

    private static final byte[] jwtKey = UUID.randomUUID().toString().replace("-", "").getBytes();

    @Test
    public void test() {
        String token = Jwts.builder()
                .claim("IP", "192.168.100.2")
                .claim("UUID", UUID.randomUUID())
                .setExpiration(new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90)))
                .signWith(Keys.hmacShaKeyFor(jwtKey))
                .compact();
        System.out.println(token);
        Claims jwt = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(jwtKey)).build()
                .parseClaimsJws(token)
                .getBody();
        System.out.println(jwt);
        System.out.println(jwt.get("IP", String.class));
    }
}
