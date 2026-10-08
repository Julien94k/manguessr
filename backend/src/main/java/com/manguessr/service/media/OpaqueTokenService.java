package com.manguessr.service.media;

import com.manguessr.model.enums.MediaSource;
import com.manguessr.model.enums.MediaTransform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Signe et verifie les jetons qui remplacent les URLs media dans les reponses de l'API.
 *
 * <h3>Pourquoi un jeton plutot que l'URL d'origine</h3>
 * Les chemins des CDN trahissent l'oeuvre a deviner. AniGuessr a le meme besoin et sert ses
 * captures via {@code screenshot.php?data=<base64>} — mais un base64 nu se decode en une
 * seconde, ce qui revele la reponse. Ici la charge utile est signee en HMAC-SHA256 : elle
 * reste illisible pour le joueur et surtout infalsifiable, donc personne ne peut demander
 * une jaquette nette alors que la manche en est au premier palier de flou.
 *
 * Format de la charge : {@code source:id:transform:parametre:expiration}, encodee en
 * base64url puis suivie de sa signature :
 * {@code base64url(charge).base64url(signature)}
 */
@Service
public class OpaqueTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final Duration validity;

    public OpaqueTokenService(@Value("${app.media.token-secret:}") String tokenSecret,
                              @Value("${jwt.secret}") String jwtSecret,
                              @Value("${app.media.token-validity-minutes:180}") long validityMinutes) {
        // A defaut de secret dedie, on derive celui du JWT : une installation sans
        // configuration supplementaire reste correctement signee.
        String secret = tokenSecret.isBlank() ? jwtSecret + ":media" : tokenSecret;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.validity = Duration.ofMinutes(validityMinutes);
    }

    public String sign(MediaSource source, long id, MediaTransform transform, int parameter) {
        long expiresAt = Instant.now().plus(validity).getEpochSecond();
        String payload = source.name() + ":" + id + ":" + transform.name()
                + ":" + parameter + ":" + expiresAt;

        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + ENCODER.encodeToString(hmac(encodedPayload));
    }

    /**
     * Verifie la signature et l'expiration.
     *
     * @throws IllegalArgumentException si le jeton est malforme, mal signe ou expire
     */
    public MediaToken verify(String token) {
        int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new IllegalArgumentException("Jeton media malforme.");
        }

        String encodedPayload = token.substring(0, separator);
        byte[] providedSignature = decode(token.substring(separator + 1));

        // Comparaison a temps constant : une comparaison naive fuiterait la signature
        // attendue octet par octet.
        if (!MessageDigest.isEqual(hmac(encodedPayload), providedSignature)) {
            throw new IllegalArgumentException("Signature de jeton media invalide.");
        }

        String payload = new String(decode(encodedPayload), StandardCharsets.UTF_8);
        String[] parts = payload.split(":");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Jeton media malforme.");
        }

        MediaToken parsed;
        try {
            parsed = new MediaToken(
                    MediaSource.valueOf(parts[0]),
                    Long.parseLong(parts[1]),
                    MediaTransform.valueOf(parts[2]),
                    Integer.parseInt(parts[3]),
                    Long.parseLong(parts[4]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Jeton media illisible.");
        }

        if (Instant.now().getEpochSecond() > parsed.expiresAt()) {
            throw new IllegalArgumentException("Jeton media expire.");
        }

        return parsed;
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Signature du jeton media impossible", e);
        }
    }

    private byte[] decode(String value) {
        try {
            return DECODER.decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Jeton media malforme.");
        }
    }
}
