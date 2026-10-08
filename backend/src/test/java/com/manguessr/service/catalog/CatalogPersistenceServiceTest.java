package com.manguessr.service.catalog;

import com.manguessr.model.entity.MediaCharacter;
import com.manguessr.model.entity.MediaImage;
import com.manguessr.model.entity.MediaTheme;
import com.manguessr.model.entity.MediaTitle;
import com.manguessr.model.entity.MediaWork;
import com.manguessr.model.enums.ImageKind;
import com.manguessr.model.enums.ThemeKind;
import com.manguessr.model.enums.TitleKind;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.util.TextNormalizer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L'ingestion se fait en plusieurs passes sur des sources differentes : AniList d'abord,
 * puis MangaDex et AnimeThemes. Une reingestion AniList ne doit pas detruire ce que les
 * autres sources ont apporte — c'est le piege principal de cette phase.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogPersistenceServiceTest {

    @Autowired
    private CatalogPersistenceService persistenceService;

    @Autowired
    private MediaWorkRepository workRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Les methodes de persistance s'executent en REQUIRES_NEW et commitent dans leur propre
     * transaction. Le contexte de persistance du test garde sinon une version perimee des
     * entites : il faut le vider pour relire ce qui a reellement ete ecrit.
     */
    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    private MediaWork newWork(int anilistId, WorkType type, String title, int popularity) {
        MediaWork work = new MediaWork();
        work.setAnilistId(anilistId);
        work.setType(type);
        work.setTitleRomaji(title);
        work.setPopularity(popularity);
        work.setCoverUrl("https://cdn.example/" + anilistId + "-cover.jpg");
        work.addTitle(new MediaTitle(title, TextNormalizer.normalize(title), TitleKind.ROMAJI));
        work.addImage(new MediaImage(ImageKind.COVER, work.getCoverUrl(), 0, null));
        return work;
    }

    @Test
    @Transactional
    void une_reingestion_anilist_preserve_les_couvertures_mangadex_et_les_generiques() {
        // 1re passe : AniList
        persistenceService.saveAll(List.of(newWork(9001, WorkType.MANGA, "Oeuvre Test", 100)));
        Long workId = workRepository.findByAnilistId(9001).orElseThrow().getId();

        // 2e passe : MangaDex ajoute des couvertures de volumes
        persistenceService.attachVolumeCovers(workId, "uuid-mangadex", List.of(
                new MangaDexClient.VolumeCover("2", "https://uploads.example/vol2.jpg"),
                new MangaDexClient.VolumeCover("3", "https://uploads.example/vol3.jpg")));
        persistenceService.attachChapterPages(workId, List.of(
                new MangaDexClient.ChapterPage("4", "https://uploads.example/data-saver/h4/p5.jpg"),
                new MangaDexClient.ChapterPage("4", "https://uploads.example/data-saver/h4/p9.jpg"),
                new MangaDexClient.ChapterPage("12", "https://uploads.example/data-saver/h12/p7.jpg")));

        // 3e passe : AnimeThemes ajoute un generique
        MediaTheme theme = new MediaTheme();
        theme.setKind(ThemeKind.OP);
        theme.setSequence(1);
        theme.setSongTitle("Chanson");
        theme.setAudioUrl("https://a.example/op1.ogg");
        persistenceService.attachThemes(workId, List.of(theme));

        // Reingestion AniList : le titre change, mais rien d'autre ne doit disparaitre.
        persistenceService.saveAll(List.of(newWork(9001, WorkType.MANGA, "Oeuvre Test Renommee", 250)));
        reloadFromDatabase();

        MediaWork reloaded = workRepository.findByAnilistId(9001).orElseThrow();

        assertThat(reloaded.getTitleRomaji()).isEqualTo("Oeuvre Test Renommee");
        assertThat(reloaded.getPopularity()).isEqualTo(250);

        // Ce que les autres sources ont apporte est intact.
        assertThat(reloaded.getMangadexId()).isEqualTo("uuid-mangadex");
        assertThat(reloaded.getImages())
                .filteredOn(image -> image.getKind() == ImageKind.VOLUME_COVER)
                .hasSize(2);
        // Les pages de chapitres alimentent le mode Images manga : les perdre le rendrait injouable.
        assertThat(reloaded.getImages())
                .filteredOn(image -> image.getKind() == ImageKind.CHAPTER_PAGE)
                .hasSize(3);
        assertThat(reloaded.getThemes()).hasSize(1);

        // Ce qu'AniList possede a bien ete remplace, sans doublon.
        assertThat(reloaded.getImages())
                .filteredOn(image -> image.getKind() == ImageKind.COVER)
                .hasSize(1);
        assertThat(reloaded.getTitles())
                .extracting(MediaTitle::getValue)
                .containsExactly("Oeuvre Test Renommee");
    }

    @Test
    @Transactional
    void une_seconde_ingestion_met_a_jour_au_lieu_de_dupliquer() {
        persistenceService.saveAll(List.of(newWork(9002, WorkType.ANIME, "Doublon", 10)));
        persistenceService.saveAll(List.of(newWork(9002, WorkType.ANIME, "Doublon", 20)));
        reloadFromDatabase();

        assertThat(workRepository.findAll())
                .filteredOn(work -> work.getAnilistId() == 9002)
                .hasSize(1);
    }

    @Test
    @Transactional
    void les_personnages_sont_remplaces_et_non_accumules() {
        MediaWork first = newWork(9003, WorkType.ANIME, "Avec Personnages", 10);
        first.addCharacter(character("Personnage A"));
        persistenceService.saveAll(List.of(first));

        MediaWork second = newWork(9003, WorkType.ANIME, "Avec Personnages", 10);
        second.addCharacter(character("Personnage B"));
        persistenceService.saveAll(List.of(second));
        reloadFromDatabase();

        assertThat(workRepository.findByAnilistId(9003).orElseThrow().getCharacters())
                .extracting(MediaCharacter::getName)
                .containsExactly("Personnage B");
    }

    @Test
    @Transactional
    void une_reingestion_conserve_les_identifiants_references_par_les_puzzles() {
        // Regression : les puzzles du jour et les parties en cours referencent ces identifiants.
        // Une reingestion qui supprimait puis recreait les lignes rendait leurs manches vides.
        MediaWork first = newWork(9004, WorkType.ANIME, "Oeuvre Figee", 10);
        first.addCharacter(character("Heroine"));
        MediaCharacter rival = character("Rival");
        rival.setAnilistId(77);
        first.addCharacter(rival);
        first.addImage(new MediaImage(ImageKind.EPISODE_THUMB, "https://cdn.example/ep1.jpg", 1, null));
        persistenceService.saveAll(List.of(first));
        Long workId = workRepository.findByAnilistId(9004).orElseThrow().getId();
        persistenceService.attachThemes(workId, List.of(theme("https://v.example/op1.webm", "https://a.example/op1.ogg")));
        reloadFromDatabase();

        MediaWork before = workRepository.findByAnilistId(9004).orElseThrow();
        List<Long> characterIds = before.getCharacters().stream().map(MediaCharacter::getId).toList();
        List<Long> imageIds = before.getImages().stream().map(MediaImage::getId).toList();
        Long themeId = before.getThemes().iterator().next().getId();

        // Reingestion : memes oeuvres, donnees mises a jour (favoris, nom, lien audio corrige).
        MediaWork second = newWork(9004, WorkType.ANIME, "Oeuvre Figee", 20);
        MediaCharacter heroine = character("Heroine");
        heroine.setFavourites(999);
        second.addCharacter(heroine);
        MediaCharacter renamedRival = character("Rival Renomme");
        renamedRival.setAnilistId(77);
        second.addCharacter(renamedRival);
        second.addImage(new MediaImage(ImageKind.EPISODE_THUMB, "https://cdn.example/ep1.jpg", 1, null));
        persistenceService.saveAll(List.of(second));
        persistenceService.attachThemes(workId, List.of(theme("https://v.example/op1.webm", "https://a.example/op1-corrige.ogg")));
        reloadFromDatabase();

        MediaWork after = workRepository.findByAnilistId(9004).orElseThrow();
        assertThat(after.getCharacters()).extracting(MediaCharacter::getId)
                .containsExactlyInAnyOrderElementsOf(characterIds);
        assertThat(after.getCharacters()).extracting(MediaCharacter::getName)
                .containsExactlyInAnyOrder("Heroine", "Rival Renomme");
        assertThat(after.getCharacters()).filteredOn(c -> "Heroine".equals(c.getName()))
                .extracting(MediaCharacter::getFavourites).containsExactly(999);
        assertThat(after.getImages()).extracting(MediaImage::getId).containsExactlyInAnyOrderElementsOf(imageIds);
        assertThat(after.getThemes()).hasSize(1);
        MediaTheme updatedTheme = after.getThemes().iterator().next();
        assertThat(updatedTheme.getId()).isEqualTo(themeId);
        assertThat(updatedTheme.getAudioUrl()).isEqualTo("https://a.example/op1-corrige.ogg");
    }

    private MediaTheme theme(String videoUrl, String audioUrl) {
        MediaTheme theme = new MediaTheme();
        theme.setKind(ThemeKind.OP);
        theme.setSequence(1);
        theme.setSongTitle("Chanson");
        theme.setVideoUrl(videoUrl);
        theme.setAudioUrl(audioUrl);
        return theme;
    }

    private MediaCharacter character(String name) {
        MediaCharacter character = new MediaCharacter();
        character.setName(name);
        character.setNormalizedName(TextNormalizer.normalize(name));
        character.setImageUrl("https://cdn.example/" + name + ".png");
        return character;
    }
}
