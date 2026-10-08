package com.manguessr.support;

import com.manguessr.model.entity.*;
import com.manguessr.model.enums.*;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.util.TextNormalizer;

/**
 * Catalogue minimal partage par les tests d'integration : trois animes par palier, chacun
 * jouable dans tous les modes anime.
 *
 * Les classes de test partagent le meme contexte Spring, donc la meme base H2 : le catalogue
 * n'est insere qu'une fois, par la premiere classe qui en a besoin.
 */
public final class TestCatalog {

    public static final String SECRET_TITLE = "Oeuvre Ultra Secrete";

    private TestCatalog() {}

    public static void seedAnimeIfEmpty(MediaWorkRepository workRepository) {
        if (workRepository.countByType(WorkType.ANIME) > 0) {
            return;
        }
        for (Difficulty tier : Difficulty.values()) {
            for (int i = 0; i < 3; i++) {
                workRepository.save(buildWork(tier, i));
            }
        }
    }

    private static MediaWork buildWork(Difficulty tier, int index) {
        MediaWork work = new MediaWork();
        work.setAnilistId(900_000 + tier.ordinal() * 10 + index);
        work.setType(WorkType.ANIME);
        String title = index == 0 && tier == Difficulty.EASY
                ? SECRET_TITLE
                : "Oeuvre " + tier + " " + index;
        work.setTitleRomaji(title);
        work.setYear(2010 + index);
        work.setAverageScore(70 + index);
        work.setPopularity(1000 - index);
        work.setSource("MANGA");
        work.setCoverUrl("https://cdn.example/cover-" + tier + index + ".jpg");
        work.setDifficultyTier(tier);
        work.setDescription("Un synopsis mentionnant " + title + " explicitement.");
        work.getGenres().add("Action");

        work.addTitle(new MediaTitle(title, TextNormalizer.normalize(title), TitleKind.ROMAJI));
        work.addTag(new MediaTag("Tragedy", 80, "Theme", false));
        work.addCredit(new MediaCredit("Studio " + index, CreditKind.STUDIO, null));

        for (int image = 0; image < 3; image++) {
            work.addImage(new MediaImage(ImageKind.EPISODE_THUMB,
                    "https://cdn.example/" + tier + index + "-" + image + ".jpg", image, null));
        }
        work.addImage(new MediaImage(ImageKind.COVER, work.getCoverUrl(), 9, null));

        for (int slot = 0; slot < 4; slot++) {
            MediaCharacter character = new MediaCharacter();
            character.setName("Perso " + tier + index + slot);
            character.setNormalizedName(TextNormalizer.normalize(character.getName()));
            character.setImageUrl("https://cdn.example/perso-" + tier + index + slot + ".png");
            character.setFavourites(100 - slot);
            work.addCharacter(character);
        }

        // Comme chez AnimeThemes, le nom de fichier porte le titre : il ne doit jamais sortir.
        MediaTheme opening = new MediaTheme();
        opening.setKind(ThemeKind.OP);
        opening.setSequence(1);
        opening.setSongTitle("Chanson " + tier + index);
        opening.setAudioUrl("https://cdn.example/" + title.replace(' ', '_') + "-OP1.ogg");
        opening.setVideoUrl("https://cdn.example/" + title.replace(' ', '_') + "-OP1.webm");
        work.addTheme(opening);

        return work;
    }
}
