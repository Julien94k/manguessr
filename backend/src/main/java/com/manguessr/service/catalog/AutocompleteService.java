package com.manguessr.service.catalog;

import com.manguessr.model.dto.AutocompleteEntry;
import com.manguessr.model.enums.TitleKind;
import com.manguessr.model.enums.WorkType;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaTitleRepository;
import com.manguessr.repository.projection.TitleEntry;
import com.manguessr.util.TextNormalizer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Index d'autocompletion servi au front.
 *
 * AniGuessr envoie la liste complete au client, qui filtre ensuite localement : c'est
 * instantane a la frappe et evite une requete par caractere. On fait pareil, avec la
 * compression gzip activee cote serveur et un cache applicatif, car la liste ne change
 * qu'a l'ingestion.
 */
@Service
public class AutocompleteService {

    private final MediaTitleRepository titleRepository;
    private final MediaCharacterRepository characterRepository;

    public AutocompleteService(MediaTitleRepository titleRepository,
                               MediaCharacterRepository characterRepository) {
        this.titleRepository = titleRepository;
        this.characterRepository = characterRepository;
    }

    /**
     * Suggestions d'un univers : <b>une par serie</b>.
     *
     * Le champ n'affiche que 5 suggestions. Deux filtrages successifs les protegent :
     * <ul>
     *   <li>les titres en langue etrangere les remplissaient (4243 synonymes cote manga) : ne
     *       sont servis que les titres <b>romaji et anglais</b>, plus les synonymes qui sont une
     *       variante du titre officiel de leur oeuvre. Les titres natifs sont ecartes, personne
     *       ne les saisit au clavier ici ;</li>
     *   <li>les declinaisons d'une serie les remplissaient a leur tour — « Attack on Titan » et
     *       ses cinq saisons, les onze parties de JoJo — alors qu'elles valent toutes la meme
     *       reponse. Une serie n'a donc qu'une entree.</li>
     * </ul>
     * Les titres ecartes de l'affichage deviennent des <b>alias</b> de leur entree : la saisie
     * les trouve toujours, sinon « Boruto » ou « Brotherhood » seraient introuvables au clavier.
     * Et tous restent <b>acceptes</b> comme reponse — la validation lit la table des titres,
     * pas cet index.
     */
    @Cacheable(value = "autocomplete-titles", key = "#type")
    public List<AutocompleteEntry> titles(WorkType type) {
        List<TitleEntry> entries = titleRepository.findAllEntriesByType(type);

        Map<Long, List<String>> officialByWork = new HashMap<>();
        for (TitleEntry entry : entries) {
            if (entry.kind() == TitleKind.ROMAJI || entry.kind() == TitleKind.ENGLISH) {
                officialByWork.computeIfAbsent(entry.workId(), key -> new ArrayList<>()).add(entry.value());
            }
        }

        Map<Object, List<TitleEntry>> bySeries = entries.stream()
                .filter(entry -> switch (entry.kind()) {
                    case ROMAJI, ENGLISH -> true;
                    case NATIVE -> false;
                    case SYNONYM -> SuggestibleTitles.isVariantOfAny(
                            entry.value(), officialByWork.getOrDefault(entry.workId(), List.of()));
                })
                // Sans serie connue, une oeuvre ne se groupe qu'avec elle-meme.
                .collect(Collectors.groupingBy(entry -> entry.seriesId() != null
                        ? entry.seriesId() : "work:" + entry.workId()));

        return bySeries.values().stream()
                .map(AutocompleteService::toEntry)
                .sorted(Comparator.comparing(AutocompleteEntry::label))
                .toList();
    }

    /**
     * Construit la suggestion d'une serie : un libelle, le reste en alias.
     *
     * Le libelle est le titre le plus <b>generique</b>, cherche en deux temps autour de
     * l'oeuvre qui nomme la serie (la plus populaire du groupe) :
     * <ol>
     *   <li>un titre que celui de cette oeuvre <b>prolonge</b> : « Dragon Ball » plutot que
     *       « Dragon Ball Z », « Fullmetal Alchemist » plutot que « Fullmetal Alchemist:
     *       Brotherhood » ;</li>
     *   <li>a defaut, un titre de cette oeuvre : « Bakemonogatari » (aucun titre de la serie
     *       ne le prolonge, mais c'est bien la le nom de la franchise), « JoJo no Kimyou na
     *       Bouken: Steel Ball Run » chez JoJo, ou aucune partie n'en prolonge une autre.</li>
     * </ol>
     * A chaque etape, le titre <b>anglais</b> passe avant le romaji, puis le plus court :
     * « Demon Slayer: Kimetsu no Yaiba » plutot que « Kimetsu no Yaiba », « Attack on Titan »
     * plutot que « Shingeki no Kyojin ». C'est la langue du titre affiche partout ailleurs
     * ({@code MediaWork.displayTitle}) — et surtout celle de l'indice « initiales » du mode
     * Images : avec « D_____ S_____ … » sous les yeux, une suggestion en romaji ne ressemblait
     * a rien. Auparavant le plus court l'emportait, et la moitie des libelles etaient en romaji.
     * <p>
     * Chercher un titre prolonge par <b>n'importe quel</b> autre serait trop large :
     * « Owarimonogatari » l'emporterait sur « Bakemonogatari », au seul motif qu'il a lui-meme
     * une suite. Un synonyme ne sert jamais de libelle — « DBZ » ou « AoT » sont les plus
     * courts de leur serie, et feraient des suggestions illisibles.
     */
    private static AutocompleteEntry toEntry(List<TitleEntry> series) {
        List<String> leadingTitles = series.stream()
                .filter(TitleEntry::isSeriesLeader)
                .map(entry -> TextNormalizer.normalize(entry.value()))
                .toList();

        TitleEntry label = series.stream()
                .filter(entry -> entry.kind() == TitleKind.ROMAJI || entry.kind() == TitleKind.ENGLISH)
                .min(labelOrder(leadingTitles))
                .orElseGet(() -> series.stream().min(labelOrder(leadingTitles)).orElseThrow());

        String labelKey = TextNormalizer.normalize(label.value());
        List<String> aliases = series.stream()
                .map(TitleEntry::value)
                .filter(value -> !TextNormalizer.normalize(value).equals(labelKey))
                .distinct()
                .sorted()
                .toList();
        return new AutocompleteEntry(label.value(), aliases);
    }

    /**
     * Titre generique d'abord, puis celui de l'oeuvre de reference, puis l'anglais, puis le
     * plus court.
     */
    private static Comparator<TitleEntry> labelOrder(List<String> leadingTitles) {
        return Comparator.comparing((TitleEntry entry) -> !isRootOf(entry, leadingTitles))
                .thenComparing(entry -> !entry.isSeriesLeader())
                .thenComparing(entry -> entry.kind() != TitleKind.ENGLISH)
                .thenComparingInt(entry -> TextNormalizer.normalize(entry.value()).length())
                .thenComparing(TitleEntry::value);
    }

    /** Le titre de l'oeuvre de reference prolonge-t-il celui-ci d'un mot ? */
    private static boolean isRootOf(TitleEntry entry, List<String> leadingTitles) {
        String root = TextNormalizer.normalize(entry.value()) + " ";
        return leadingTitles.stream().anyMatch(title -> title.startsWith(root));
    }

    /** Les personnages n'ont pas de serie : une entree par nom, sans alias. */
    @Cacheable(value = "autocomplete-characters", key = "#type")
    public List<AutocompleteEntry> characterNames(WorkType type) {
        return characterRepository.findAllNamesByType(type).stream()
                .map(AutocompleteEntry::of)
                .toList();
    }

    /** Appele en fin d'ingestion : sans cela le front continuerait a servir l'ancien index. */
    @CacheEvict(value = {"autocomplete-titles", "autocomplete-characters"}, allEntries = true)
    public void invalidate() {
        // L'annotation fait tout le travail.
    }
}
