package com.crowdfunding.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.crowdfunding.backend.entity.User;
import com.crowdfunding.backend.login.UserAuthDetails;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;

/**
 * UT-14 · Ciclo de vida del token JWT.
 * Componente bajo prueba: JwtService.
 * Trazabilidad: HU-1.1.2 (CA1) y HU-1.1.6 (CA6, control de acceso).
 *
 * Los campos @Value se pueblan con ReflectionTestUtils para no levantar el contexto
 * de Spring. Un expirationSeconds negativo permite fabricar un token vencido al
 * instante, sin esperas ni sleeps.
 */
@DisplayName("UT-14 · JwtService")
class JwtServiceTest {

    private static final String EMAIL = "camila@example.com";
    private static final String OTRO_EMAIL = "ana.gomez@example.com";
    private static final long VEINTICUATRO_HORAS = 86400L;

    private JwtService service;
    private String secret;

    @BeforeEach
    void setUp() {
        secret = claveBase64(32); // 256 bits, mínimo exigido por HS256
        service = nuevoServicio(secret, VEINTICUATRO_HORAS);
    }

    // ---------- emisión y lectura de claims ----------
    @Test
    @DisplayName("UT-14 · El token se emite con el correo del usuario como sujeto")
    void emiteTokenConElCorreoComoSujeto() {
        String token = service.generateToken(EMAIL);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        assertThat(service.extractUsername(token)).isEqualTo(EMAIL);
        assertThat(service.extractClaim(token, Claims::getSubject)).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("UT-14 · CA1 · La expiración declarada es de 24 horas (86400 s)")
    void expiracionYDuracionDeclarada() {
        long antes = System.currentTimeMillis();
        String token = service.generateToken(EMAIL);

        assertThat(service.getExpirationSeconds()).isEqualTo(VEINTICUATRO_HORAS);

        // El claim exp se serializa en segundos, por eso se admite una tolerancia de 2 s.
        long esperado = antes + VEINTICUATRO_HORAS * 1000;
        assertThat(service.extractExpiration(token).getTime())
                .isBetween(esperado - 2000, esperado + 2000);
    }

    // ---------- validación contra el usuario ----------
    @Test
    @DisplayName("UT-14 · El token es válido para su titular")
    void validaTokenDelTitular() {
        String token = service.generateToken(EMAIL);

        assertThat(service.validateToken(token, detalles(EMAIL))).isTrue();
    }

    @Test
    @DisplayName("UT-14 · El token de un usuario no sirve para autenticar a otro")
    void rechazaTokenDeOtroUsuario() {
        String token = service.generateToken(EMAIL);

        assertThat(service.validateToken(token, detalles(OTRO_EMAIL))).isFalse();
    }

    // ---------- expiración ----------
    @Test
    @DisplayName("UT-14 · Un token vencido lanza ExpiredJwtException al leer el sujeto")
    void tokenVencidoLanzaExcepcionAlExtraerSujeto() {
        String tokenVencido = nuevoServicio(secret, -1L).generateToken(EMAIL);

        assertThatThrownBy(() -> service.extractUsername(tokenVencido))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("UT-14 · validateToken PROPAGA la excepción con un token vencido, no devuelve false")
    void validateTokenPropagaLaExcepcion() {
        String tokenVencido = nuevoServicio(secret, -1L).generateToken(EMAIL);
        UserAuthDetails titular = detalles(EMAIL);

        // extractUsername se ejecuta antes que isTokenExpired, y parseClaimsJws ya
        // valida la vigencia. La rama "return false" por expiración es inalcanzable.
        assertThatThrownBy(() -> service.validateToken(tokenVencido, titular))
                .isInstanceOf(ExpiredJwtException.class);
    }

    // ---------- integridad de la firma ----------
    @Test
    @DisplayName("UT-14 · Un token firmado con otra clave es rechazado")
    void rechazaTokenFirmadoConOtraClave() {
        JwtService otroEmisor = nuevoServicio(claveBase64(32), VEINTICUATRO_HORAS);
        String tokenAjeno = otroEmisor.generateToken(EMAIL);

        assertThatThrownBy(() -> service.extractUsername(tokenAjeno))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("UT-14 · Alterar el sujeto del payload invalida la firma")
    void rechazaTokenConPayloadAlterado() {
        String[] partes = service.generateToken(EMAIL).split("\\.");
        String payloadFalso = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + OTRO_EMAIL + "\",\"exp\":9999999999}")
                        .getBytes(StandardCharsets.UTF_8));
        String tokenAlterado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertThatThrownBy(() -> service.extractUsername(tokenAlterado))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("UT-14 · Una cadena que no es un JWT es rechazada")
    void rechazaTokenMalformado() {
        assertThatThrownBy(() -> service.extractUsername("esto-no-es-un-jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }

    // ---------- configuración de la clave ----------
    @Test
    @DisplayName("UT-14 · Una clave de menos de 256 bits se rechaza al firmar con HS256")
    void rechazaClaveDebil() {
        JwtService claveDebil = nuevoServicio(claveBase64(16), VEINTICUATRO_HORAS);

        assertThatThrownBy(() -> claveDebil.generateToken(EMAIL))
                .isInstanceOf(WeakKeyException.class);
    }

    // ---------- auxiliares ----------
    private static String claveBase64(int bytes) {
        byte[] material = new byte[bytes];
        new SecureRandom().nextBytes(material);
        return Base64.getEncoder().encodeToString(material);
    }

    private static JwtService nuevoServicio(String secret, long expirationSeconds) {
        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "secret", secret);
        ReflectionTestUtils.setField(svc, "expirationSeconds", expirationSeconds);
        return svc;
    }

    /** Se usa el adaptador real del proyecto, el mismo que entrega CustomUserDetailsService. */
    private static UserAuthDetails detalles(String email) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hashDePruebaNoValidoParaLogin");
        return new UserAuthDetails(user);
    }
}