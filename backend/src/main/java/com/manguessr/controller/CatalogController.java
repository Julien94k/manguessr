package com.manguessr.controller;

import com.manguessr.model.dto.AutocompleteEntry;
import com.manguessr.model.dto.AutocompleteResponse;
import com.manguessr.model.dto.CatalogStatsResponse;
import com.manguessr.model.enums.ImageKind;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaWorkRepository;
import com.manguessr.service.catalog.AutocompleteService;
import com.manguessr.service.catalog.CatalogIngestionService;
import com.manguessr.service.catalog.IngestionStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Consultation du catalogue et pilotage de l'ingestion.
 *
 * L'autocompletion est publique (on peut jouer sans compte) ; declencher une ingestion
 * est reserve aux administrateurs, car c'est une operation longue et couteuse en quota.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    /** Le mode Images demande 3 images, le mode Personnages 4 portraits. */
    private static final long MIN_IMAGES_FOR_GAME = 3;
    private static final long MIN_CHARACTERS_FOR_GAME = 4;

    private final AutocompleteService autocompleteService;
    private final CatalogIngestionService ingestionService;
    private final MediaWorkRepository workRepository;

    public CatalogController(AutocompleteService autocompleteService,
                             CatalogIngestionService ingestionService,
                             MediaWorkRepository workRepository) {
        this.autocompleteService = autocompleteService;
        this.ingestionService = ingestionService;
        this.workRepository = workRepository;
    }

    /**
     * Index d'autocompletion complet pour un univers.
     *
     * @param type   anime ou manga
     * @param target "title" (defaut) ou "character"
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(
            @RequestParam String type,
            @RequestParam(defaultValue = "title") String target) {

        WorkType workType = parseType(type);
        List<AutocompleteEntry> entries = switch (target.toLowerCase(Locale.ROOT)) {
            case "title" -> autocompleteService.titles(workType);
            case "character" -> autocompleteService.characterNames(workType);
            default -> throw new IllegalArgumentException(
                    "Cible d'autocompletion inconnue : '" + target + "'. Attendu : title ou character.");
        };

        // L'index ne change qu'a l'ingestion : le navigateur peut le garder une heure.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(new AutocompleteResponse(workType.name().toLowerCase(Locale.ROOT), entries, entries.size()));
    }

    /** Volumetrie du catalogue, mode de jeu par mode de jeu. */
    @GetMapping("/catalog/stats")
    public CatalogStatsResponse stats() {
        return new CatalogStatsResponse(
                workRepository.countByType(WorkType.ANIME),
                workRepository.countByType(WorkType.MANGA),
                workRepository.countWithAtLeastImages(WorkType.ANIME, ImageKind.EPISODE_THUMB, MIN_IMAGES_FOR_GAME),
                workRepository.countWithAtLeastCharacters(WorkType.ANIME, MIN_CHARACTERS_FOR_GAME),
                workRepository.countAnimeWithThemes(),
                workRepository.countWithAtLeastImages(WorkType.MANGA, ImageKind.CHAPTER_PAGE, MIN_IMAGES_FOR_GAME));
    }

    /** Avancement de l'ingestion. Reserve aux admins : le journal cite les erreurs des sources. */
    @GetMapping("/catalog/ingestion")
    @PreAuthorize("hasRole('ADMIN')")
    public IngestionStatus ingestionStatus() {
        return ingestionService.getStatus();
    }

    /** Lance une ingestion en tache de fond. Reservee aux administrateurs. */
    @PostMapping("/catalog/ingestion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> startIngestion() {
        ingestionService.ingestAsync();
        return ResponseEntity.accepted()
                .body(Map.of("message", "Ingestion demarree. Suivre GET /api/catalog/ingestion."));
    }

    private WorkType parseType(String value) {
        try {
            return WorkType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Univers inconnu : '" + value + "'. Attendu : anime ou manga.");
        }
    }
}
