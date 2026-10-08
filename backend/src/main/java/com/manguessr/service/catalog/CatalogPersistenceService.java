package com.manguessr.service.catalog;

import com.manguessr.model.entity.*;
import com.manguessr.model.enums.Difficulty;
import com.manguessr.model.enums.ImageKind;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaWorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ecritures du catalogue, isolees dans leur propre service.
 *
 * Deux raisons a cette separation :
 * <ul>
 *   <li>{@code @Transactional} ne s'applique pas aux appels internes a une meme classe ;
 *       l'ingestion doit donc passer par un bean distinct</li>
 *   <li>chaque page est commitee independamment : une coupure au milieu d'une ingestion
 *       de 20 pages conserve les 19 precedentes</li>
 * </ul>
 */
@Service
public class CatalogPersistenceService {

    /** Lots de mise a jour des series : un « in » de 4000 identifiants est refuse par Postgres. */
    private static final int SERIES_BATCH_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(CatalogPersistenceService.class);

    /**
     * Natures d'images produites par AniList. Un reingest ne remplace que celles-la :
     * les couvertures de volumes et pages de chapitres MangaDex, ingerees dans des etapes
     * ulterieures, sont preservees.
     */
    private static final Set<ImageKind> ANILIST_OWNED_IMAGES =
            EnumSet.of(ImageKind.EPISODE_THUMB, ImageKind.BANNER, ImageKind.COVER);

    private final MediaWorkRepository workRepository;
    private final MediaCharacterRepository characterRepository;

    public CatalogPersistenceService(MediaWorkRepository workRepository,
                                     MediaCharacterRepository characterRepository) {
        this.workRepository = workRepository;
        this.characterRepository = characterRepository;
    }

    /**
     * Insere ou met a jour un lot d'oeuvres.
     *
     * @return le nombre d'oeuvres traitees
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveAll(List<MediaWork> incoming) {
        List<Integer> anilistIds = incoming.stream().map(MediaWork::getAnilistId).toList();
        Map<Integer, MediaWork> existingByAnilistId = workRepository.findByAnilistIdIn(anilistIds).stream()
                .collect(Collectors.toMap(MediaWork::getAnilistId, Function.identity()));

        for (MediaWork candidate : incoming) {
            MediaWork existing = existingByAnilistId.get(candidate.getAnilistId());
            if (existing == null) {
                workRepository.save(candidate);
            } else {
                merge(existing, candidate);
                workRepository.save(existing);
            }
        }

        return incoming.size();
    }

    /**
     * Reporte sur l'entite persistee les donnees dont AniList est proprietaire.
     *
     * Ce qui est volontairement conserve : {@code mangadexId}, les couvertures de volumes
     * et les generiques. Ces donnees viennent d'autres sources et seraient perdues si l'on
     * remplacait tout aveuglement.
     *
     * Personnages et images sont mis a jour <b>sur place</b>, jamais supprimes puis recrees :
     * les puzzles quotidiens figes et les parties en cours referencent leurs identifiants.
     * Les recreer donnait des manches vides (portraits, images, generiques introuvables) pour
     * tous les puzzles generes avant l'ingestion.
     */
    private void merge(MediaWork existing, MediaWork candidate) {
        existing.setMalId(candidate.getMalId());
        existing.setTitleRomaji(candidate.getTitleRomaji());
        existing.setTitleEnglish(candidate.getTitleEnglish());
        existing.setTitleNative(candidate.getTitleNative());
        existing.setYear(candidate.getYear());
        existing.setFormat(candidate.getFormat());
        existing.setStatus(candidate.getStatus());
        existing.setSource(candidate.getSource());
        existing.setCountryOfOrigin(candidate.getCountryOfOrigin());
        existing.setAverageScore(candidate.getAverageScore());
        existing.setPopularity(candidate.getPopularity());
        existing.setFavourites(candidate.getFavourites());
        existing.setAdaptationPopularity(candidate.getAdaptationPopularity());
        existing.setEpisodes(candidate.getEpisodes());
        existing.setChapters(candidate.getChapters());
        existing.setVolumes(candidate.getVolumes());
        existing.setDescription(candidate.getDescription());
        existing.setCoverUrl(candidate.getCoverUrl());
        existing.setCoverColor(candidate.getCoverColor());
        existing.setBannerUrl(candidate.getBannerUrl());
        existing.setNsfw(candidate.isNsfw());
        existing.setUpdatedAt(Instant.now());

        existing.getGenres().clear();
        existing.getGenres().addAll(candidate.getGenres());

        existing.getTitles().clear();
        candidate.getTitles().forEach(existing::addTitle);

        existing.getTags().clear();
        candidate.getTags().forEach(existing::addTag);

        existing.getCredits().clear();
        candidate.getCredits().forEach(existing::addCredit);

        syncCharacters(existing, candidate);
        syncAniListImages(existing, candidate);
    }

    /** Identite stable d'un personnage : son id AniList, a defaut son nom normalise. */
    private static String characterKey(MediaCharacter character) {
        return character.getAnilistId() != null
                ? "anilist:" + character.getAnilistId()
                : "nom:" + character.getNormalizedName();
    }

    private void syncCharacters(MediaWork existing, MediaWork candidate) {
        Map<String, MediaCharacter> current = new HashMap<>();
        existing.getCharacters().forEach(character -> current.putIfAbsent(characterKey(character), character));

        Set<String> kept = new HashSet<>();
        for (MediaCharacter incoming : List.copyOf(candidate.getCharacters())) {
            String key = characterKey(incoming);
            MediaCharacter target = current.get(key);
            if (target == null) {
                existing.addCharacter(incoming);
            } else {
                target.setName(incoming.getName());
                target.setNormalizedName(incoming.getNormalizedName());
                target.getAlternativeNames().clear();
                target.getAlternativeNames().addAll(incoming.getAlternativeNames());
                target.setImageUrl(incoming.getImageUrl());
                target.setFavourites(incoming.getFavourites());
                target.setRole(incoming.getRole());
            }
            kept.add(key);
        }
        // Seuls les personnages disparus de la source sont retires.
        existing.getCharacters().removeIf(character -> !kept.contains(characterKey(character)));
    }

    /** Identite d'une image : sa nature et son URL d'origine. */
    private static String imageKey(MediaImage image) {
        return image.getKind() + "|" + image.getUrl();
    }

    /** Synchronise les images dont AniList est proprietaire ; les autres ne sont pas touchees. */
    private void syncAniListImages(MediaWork existing, MediaWork candidate) {
        Set<String> incomingKeys = new HashSet<>();
        candidate.getImages().forEach(image -> incomingKeys.add(imageKey(image)));

        existing.getImages().removeIf(image ->
                ANILIST_OWNED_IMAGES.contains(image.getKind()) && !incomingKeys.contains(imageKey(image)));

        Map<String, MediaImage> current = new HashMap<>();
        existing.getImages().forEach(image -> current.putIfAbsent(imageKey(image), image));

        for (MediaImage incoming : List.copyOf(candidate.getImages())) {
            MediaImage target = current.get(imageKey(incoming));
            if (target == null) {
                existing.addImage(incoming);
            } else {
                target.setOrdinal(incoming.getOrdinal());
            }
        }
    }

    /** Attache les couvertures de volumes MangaDex, en remplacant celles deja presentes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachVolumeCovers(Long workId, String mangadexId, List<MangaDexClient.VolumeCover> covers) {
        Optional<MediaWork> found = workRepository.findById(workId);
        if (found.isEmpty()) {
            return;
        }

        MediaWork work = found.get();
        work.setMangadexId(mangadexId);
        work.getImages().removeIf(image -> image.getKind() == ImageKind.VOLUME_COVER);

        int ordinal = 0;
        for (MangaDexClient.VolumeCover cover : covers) {
            work.addImage(new MediaImage(ImageKind.VOLUME_COVER, cover.url(), ordinal++, cover.volume()));
        }

        workRepository.save(work);
    }

    /** Attache les pages de chapitres MangaDex, en remplacant celles deja presentes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachChapterPages(Long workId, List<MangaDexClient.ChapterPage> pages) {
        Optional<MediaWork> found = workRepository.findById(workId);
        if (found.isEmpty()) {
            return;
        }

        MediaWork work = found.get();
        work.getImages().removeIf(image -> image.getKind() == ImageKind.CHAPTER_PAGE);

        int ordinal = 0;
        for (MangaDexClient.ChapterPage page : pages) {
            // Le champ « volume » porte ici le numero de chapitre, a titre informatif.
            work.addImage(new MediaImage(ImageKind.CHAPTER_PAGE, page.url(), ordinal++, page.chapter()));
        }

        workRepository.save(work);
    }

    /**
     * Attache les generiques AnimeThemes.
     *
     * Mise a jour sur place, identifiee par l'URL de la video : les manches Opening et Ending
     * figees referencent l'identifiant du generique (voir {@link #merge}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachThemes(Long workId, List<MediaTheme> themes) {
        Optional<MediaWork> found = workRepository.findById(workId);
        if (found.isEmpty()) {
            return;
        }

        MediaWork work = found.get();
        Map<String, MediaTheme> current = new HashMap<>();
        work.getThemes().forEach(theme -> current.putIfAbsent(themeKey(theme), theme));

        Set<String> kept = new HashSet<>();
        for (MediaTheme incoming : themes) {
            String key = themeKey(incoming);
            MediaTheme target = current.get(key);
            if (target == null) {
                work.addTheme(incoming);
            } else {
                target.setKind(incoming.getKind());
                target.setSequence(incoming.getSequence());
                target.setSongTitle(incoming.getSongTitle());
                target.setArtist(incoming.getArtist());
                target.setAudioUrl(incoming.getAudioUrl());
                target.setVideoUrl(incoming.getVideoUrl());
            }
            kept.add(key);
        }
        work.getThemes().removeIf(theme -> !kept.contains(themeKey(theme)));

        workRepository.save(work);
    }

    /**
     * Fige le groupe de serie de chaque oeuvre.
     *
     * Ecrit meme quand le groupe ne change pas de valeur : une oeuvre nouvellement ingeree
     * porte son propre identifiant par defaut, et c'est cette etape qui la rattache a sa serie.
     *
     * @param seriesByAnilistId oeuvre -> representant de sa serie (voir {@code SeriesGrouper})
     * @return nombre d'oeuvres dont le groupe a change
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int applySeries(Map<Integer, Integer> seriesByAnilistId) {
        List<Integer> anilistIds = List.copyOf(seriesByAnilistId.keySet());
        int changed = 0;
        for (int from = 0; from < anilistIds.size(); from += SERIES_BATCH_SIZE) {
            List<Integer> batch = anilistIds.subList(from, Math.min(from + SERIES_BATCH_SIZE, anilistIds.size()));
            for (MediaWork work : workRepository.findByAnilistIdIn(batch)) {
                Integer series = seriesByAnilistId.get(work.getAnilistId());
                if (series != null && !series.equals(work.getSeriesId())) {
                    work.setSeriesId(series);
                    changed++;
                }
            }
        }
        return changed;
    }

    /**
     * Marque les apparitions d'un univers : fiches d'un personnage hors de son oeuvre d'origine
     * (cameo, crossover), que le jeu ne tire jamais. A appeler apres le regroupement en series.
     *
     * @return nombre de fiches marquees
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int applyGuestAppearances(WorkType type) {
        List<CharacterHomes.Appearance> appearances = characterRepository.findAppearancesByType(type).stream()
                .map(row -> new CharacterHomes.Appearance(
                        (Long) row[0], (Integer) row[1], (String) row[2],
                        (Integer) row[3], (Integer) row[4], (Integer) row[5]))
                .toList();
        List<Long> guests = List.copyOf(CharacterHomes.guests(appearances));

        characterRepository.clearGuestsByType(type);
        for (int from = 0; from < guests.size(); from += SERIES_BATCH_SIZE) {
            characterRepository.markGuests(guests.subList(from, Math.min(from + SERIES_BATCH_SIZE, guests.size())));
        }
        return guests.size();
    }

    /** Applique un palier de difficulte a un lot d'identifiants ; {@code null} retire le palier. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyDifficultyTier(Difficulty tier, List<Long> workIds) {
        if (workIds.isEmpty()) {
            return;
        }
        for (MediaWork work : workRepository.findAllById(workIds)) {
            work.setDifficultyTier(tier);
        }
        log.debug("Palier {} applique a {} oeuvres", tier, workIds.size());
    }

    /** Identite d'un generique : l'URL de sa video, a defaut sa nature et son numero. */
    private static String themeKey(MediaTheme theme) {
        return theme.getVideoUrl() != null
                ? "video:" + theme.getVideoUrl()
                : theme.getKind() + ":" + theme.getSequence();
    }
}
