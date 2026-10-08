package com.manguessr.service.game;

import com.manguessr.model.entity.MediaCharacter;
import com.manguessr.model.entity.MediaTitle;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.TitleKind;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaTitleRepository;
import com.manguessr.util.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Accepter une bonne reponse est le point le plus sensible du jeu : un refus injustifie
 * se lit comme un bug flagrant cote joueur. Ces cas viennent des donnees reellement
 * ingerees (synonymes AniList de Shingeki no Kyojin, personnages de Nichijou).
 */
class AnswerMatcherTest {

    private static final int AOT_SERIES = 16498;

    private MediaTitleRepository titleRepository;
    private AnswerMatcher matcher;
    private MediaWork attackOnTitan;

    @BeforeEach
    void setUp() {
        titleRepository = mock(MediaTitleRepository.class);
        matcher = new AnswerMatcher(titleRepository, mock(MediaCharacterRepository.class));

        attackOnTitan = new MediaWork();
        attackOnTitan.setType(WorkType.ANIME);
        attackOnTitan.setTitleRomaji("Shingeki no Kyojin");
        // Echantillon reel de l'index construit a l'ingestion.
        addTitle(attackOnTitan, "Shingeki no Kyojin", TitleKind.ROMAJI);
        addTitle(attackOnTitan, "Attack on Titan", TitleKind.ENGLISH);
        addTitle(attackOnTitan, "進撃の巨人", TitleKind.NATIVE);
        addTitle(attackOnTitan, "SnK", TitleKind.SYNONYM);
        addTitle(attackOnTitan, "L’Attaque des Titans", TitleKind.SYNONYM);
    }

    private void addTitle(MediaWork work, String value, TitleKind kind) {
        work.addTitle(new MediaTitle(value, TextNormalizer.normalize(value), kind));
    }

    @ParameterizedTest(name = "\"{0}\" doit etre accepte")
    @ValueSource(strings = {
            "Shingeki no Kyojin",
            "shingeki no kyojin",
            "SHINGEKI NO KYOJIN",
            "  Shingeki no Kyojin  ",
            // Titre anglais officiel
            "Attack on Titan",
            // Titre natif
            "進撃の巨人",
            // Abreviation fournie par AniList
            "SnK",
            // Titre francais, avec et sans apostrophe typographique
            "L’Attaque des Titans",
            "L'Attaque des Titans",
            "LAttaque des Titans",
            // Faute de frappe simple
            "Shingeki no Kyojn",
    })
    void toutes_les_graphies_officielles_sont_acceptees(String answer) {
        assertThat(matcher.matchesWork(answer, attackOnTitan)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" doit etre refuse")
    @ValueSource(strings = {
            "Naruto",
            "One Piece",
            // Saisie vide ou purement symbolique
            "",
            "   ",
            "!!!",
    })
    void une_mauvaise_reponse_est_refusee(String answer) {
        assertThat(matcher.matchesWork(answer, attackOnTitan)).isFalse();
    }

    @Test
    void le_titre_de_la_serie_repond_pour_une_saison() {
        // Cas signale par un joueur : tomber sur la saison 2 et perdre en repondant le titre
        // de la serie. Les titres acceptes sont ceux de toute la franchise.
        MediaWork season2 = new MediaWork();
        season2.setType(WorkType.ANIME);
        season2.setSeriesId(AOT_SERIES);
        addTitle(season2, "Shingeki no Kyojin Season 2", TitleKind.ROMAJI);
        when(titleRepository.findNormalizedBySeriesId(AOT_SERIES)).thenReturn(Set.of(
                TextNormalizer.normalize("Shingeki no Kyojin"),
                TextNormalizer.normalize("Attack on Titan"),
                TextNormalizer.normalize("Shingeki no Kyojin Season 2")));

        assertThat(matcher.matchesWork("Attack on Titan", season2)).isTrue();
        assertThat(matcher.matchesWork("Shingeki no Kyojin", season2)).isTrue();
        // Le titre exact de la saison reste evidemment valable.
        assertThat(matcher.matchesWork("Shingeki no Kyojin Season 2", season2)).isTrue();
        assertThat(matcher.matchesWork("Death Note", season2)).isFalse();
    }

    @Test
    void une_oeuvre_sans_serie_reste_devinable() {
        // Catalogue anterieur au regroupement : la requete de serie n'est pas jouee, et les
        // titres de l'oeuvre elle-meme suffisent.
        assertThat(attackOnTitan.getSeriesId()).isNull();
        assertThat(matcher.matchesWork("Attack on Titan", attackOnTitan)).isTrue();
        verifyNoInteractions(titleRepository);
    }

    @Test
    void la_tolerance_aux_fautes_ne_confond_pas_deux_titres_courts() {
        MediaWork shortTitle = new MediaWork();
        shortTitle.setType(WorkType.ANIME);
        addTitle(shortTitle, "Air", TitleKind.ROMAJI);

        // A trois lettres, une distance de 1 changerait completement le titre.
        assertThat(matcher.matchesWork("Air", shortTitle)).isTrue();
        assertThat(matcher.matchesWork("Air2", shortTitle)).isFalse();
        assertThat(matcher.matchesWork("Bir", shortTitle)).isFalse();
    }

    @Test
    void un_personnage_est_reconnu_par_ses_variantes_de_nom() {
        MediaCharacter yuuko = new MediaCharacter();
        yuuko.setName("Aioi, Yuuko");
        yuuko.setNormalizedName(TextNormalizer.normalize("Aioi, Yuuko"));
        yuuko.getAlternativeNames().add("Yukko");

        assertThat(matcher.matchesCharacter("Aioi, Yuuko", yuuko)).isTrue();
        // Ordre inverse : reconstruit par le matcher, AniList ne le fournit pas toujours.
        assertThat(matcher.matchesCharacter("Yuuko Aioi", yuuko)).isTrue();
        // Surnom fourni dans les alternatives
        assertThat(matcher.matchesCharacter("Yukko", yuuko)).isTrue();
        assertThat(matcher.matchesCharacter("Mio Akiyama", yuuko)).isFalse();
    }

    @Test
    void un_personnage_sans_virgule_reste_reconnu() {
        MediaCharacter levi = new MediaCharacter();
        levi.setName("Levi");
        levi.setNormalizedName(TextNormalizer.normalize("Levi"));

        assertThat(matcher.matchesCharacter("Levi", levi)).isTrue();
        assertThat(matcher.matchesCharacter("levi", levi)).isTrue();
        assertThat(matcher.matchesCharacter("Eren", levi)).isFalse();
    }
}
