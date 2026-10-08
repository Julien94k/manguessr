package com.manguessr.service.game.mode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manguessr.config.GameProperties;
import com.manguessr.model.entity.MediaImage;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.entity.SessionRound;
import com.manguessr.model.enums.ImageKind;
import com.manguessr.model.enums.MediaSource;
import com.manguessr.model.enums.MediaTransform;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.game.AnswerMatcher;
import com.manguessr.service.game.ScoreCalculator;
import com.manguessr.service.media.MediaToken;
import com.manguessr.service.media.OpaqueTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Prechauffage du cache d'images.
 *
 * Le premier appel a une image coute environ une seconde (le CDN d'origine repond en 0,4 a
 * 0,8 s), les suivants 4 ms. Preparer les indices encore verrouilles est donc ce qui rend
 * la manche fluide — a condition de preparer <b>exactement</b> les rendus qui seront servis.
 */
class ImagesModeHandlerTest {

    private ImagesModeHandler handler;
    private RoundSupport support;

    @BeforeEach
    void setUp() {
        support = new RoundSupport(new ObjectMapper(), mock(OpaqueTokenService.class));
        handler = new ImagesModeHandler(mock(MediaWorkRepository.class), mock(AnswerMatcher.class),
                mock(ScoreCalculator.class), new GameProperties(), support);
    }

    private SessionRound roundOf(ImageKind kind, int cropSeed) {
        MediaWork work = new MediaWork();
        work.setType(WorkType.MANGA);
        for (long id = 1; id <= 3; id++) {
            MediaImage image = new MediaImage();
            image.setId(id);
            image.setKind(kind);
            image.setUrl("https://cdn.example/" + id + ".jpg");
            image.setOrdinal((int) id);
            work.addImage(image);
        }

        SessionRound round = new SessionRound();
        round.setWork(work);
        round.setPayload(support.serialize(RoundPayload.images(List.of(1L, 2L, 3L), cropSeed)));
        // Aucun indice debloque : seule la premiere image est visible.
        round.setCluesUnlocked(0);
        return round;
    }

    @Test
    void les_images_encore_verrouillees_sont_preparees_aussi() {
        List<MediaToken> warm = handler.mediaToWarm(roundOf(ImageKind.CHAPTER_PAGE, 0));

        assertThat(warm).extracting(MediaToken::id).containsExactly(1L, 2L, 3L);
        assertThat(warm).extracting(MediaToken::source).containsOnly(MediaSource.IMAGE);
    }

    @Test
    void une_page_de_chapitre_est_preparee_sans_recadrage() {
        assertThat(handler.mediaToWarm(roundOf(ImageKind.CHAPTER_PAGE, 7)))
                .allSatisfy(token -> {
                    assertThat(token.transform()).isEqualTo(MediaTransform.RAW);
                    assertThat(token.parameter()).isZero();
                });
    }

    @Test
    void une_couverture_de_volume_est_preparee_avec_sa_graine_de_recadrage() {
        // Parties anterieures au 14/09/2026 : leurs couvertures restent recadrees, et la graine
        // varie par image. Preparer le mauvais rendu remplirait le cache pour rien.
        List<MediaToken> warm = handler.mediaToWarm(roundOf(ImageKind.VOLUME_COVER, 7));

        assertThat(warm).extracting(MediaToken::transform).containsOnly(MediaTransform.CROP);
        assertThat(warm).extracting(MediaToken::parameter).containsExactly(7, 8, 9);
    }
}
