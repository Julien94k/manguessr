package com.manguessr.service.media;

import com.manguessr.model.enums.MediaSource;
import com.manguessr.model.enums.MediaTransform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le jeton porte sa propre autorisation : l'endpoint media est public, c'est la signature
 * qui empeche un joueur de reclamer une jaquette nette au premier essai.
 */
class OpaqueTokenServiceTest {

    private static final String SECRET =
            "un_secret_de_test_suffisamment_long_pour_hmac_sha256_aaaaaaaa";

    private OpaqueTokenService service;

    @BeforeEach
    void setUp() {
        service = new OpaqueTokenService(SECRET, "secret_jwt_inutilise_ici", 180);
    }

    @Test
    void un_jeton_signe_se_relit_a_l_identique() {
        String token = service.sign(MediaSource.IMAGE, 42L, MediaTransform.BLUR, 3);
        MediaToken verified = service.verify(token);

        assertThat(verified.source()).isEqualTo(MediaSource.IMAGE);
        assertThat(verified.id()).isEqualTo(42L);
        assertThat(verified.transform()).isEqualTo(MediaTransform.BLUR);
        assertThat(verified.parameter()).isEqualTo(3);
    }

    @Test
    void le_jeton_ne_laisse_pas_deviner_l_oeuvre() {
        String token = service.sign(MediaSource.IMAGE, 42L, MediaTransform.BLUR, 3);

        // Aucune URL ni identifiant d'oeuvre lisible en clair dans le jeton.
        assertThat(token).doesNotContain("http", "anilist", "mangadex");
    }

    @Test
    void une_charge_utile_modifiee_est_rejetee() {
        String token = service.sign(MediaSource.IMAGE, 42L, MediaTransform.BLUR, 8);
        String payload = token.substring(0, token.lastIndexOf('.'));
        String signature = token.substring(token.lastIndexOf('.'));

        // Un joueur qui reforge la charge pour demander un flou nul doit etre bloque.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("IMAGE:42:BLUR:0:99999999999"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.verify(forgedPayload + signature))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Signature");

        // La charge d'origine, elle, reste valide.
        assertThat(service.verify(payload + signature).parameter()).isEqualTo(8);
    }

    @Test
    void un_jeton_signe_avec_un_autre_secret_est_rejete() {
        String foreignToken = new OpaqueTokenService(
                "un_autre_secret_tout_aussi_long_pour_hmac_sha256_bbbbbbbb", "x", 180)
                .sign(MediaSource.IMAGE, 42L, MediaTransform.RAW, 0);

        assertThatThrownBy(() -> service.verify(foreignToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_jeton_expire_est_rejete() {
        OpaqueTokenService expiring = new OpaqueTokenService(SECRET, "x", -1);
        String token = expiring.sign(MediaSource.IMAGE, 42L, MediaTransform.RAW, 0);

        assertThatThrownBy(() -> expiring.verify(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expire");
    }

    @Test
    void un_portrait_et_une_image_de_meme_numero_ne_se_confondent_pas() {
        // Sans discriminant de source, un identifiant de personnage resoudrait vers
        // l'image portant le meme numero, donc vers une autre oeuvre.
        String imageToken = service.sign(MediaSource.IMAGE, 7L, MediaTransform.RAW, 0);
        String characterToken = service.sign(MediaSource.CHARACTER, 7L, MediaTransform.RAW, 0);

        assertThat(imageToken).isNotEqualTo(characterToken);
        assertThat(service.verify(imageToken).source()).isEqualTo(MediaSource.IMAGE);
        assertThat(service.verify(characterToken).source()).isEqualTo(MediaSource.CHARACTER);
    }

    @Test
    void un_jeton_malforme_est_rejete_sans_exception_technique() {
        for (String invalid : new String[]{"", "sans-point", ".", "abc.", "!!!.???"}) {
            assertThatThrownBy(() -> service.verify(invalid))
                    .as("jeton invalide : '%s'", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
