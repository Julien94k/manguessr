package com.manguessr.config;

import com.manguessr.model.enums.WorkType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * Rangs de coupure des paliers de difficulte, <b>par univers</b>.
 *
 * Ils different parce que les deux catalogues ne se ressemblent pas. Mesure du 19/09/2026 sur
 * 2000 oeuvres, au score de notoriete ({@code RecognitionScore}) :
 * <ul>
 *   <li>cote <b>anime</b>, le rang 1500 est « Squid Girl » et le rang 2000 un special d'Elfen
 *       Lied : on reste dans du reconnaissable tres loin dans le classement ;</li>
 *   <li>cote <b>manga</b>, le rang 1000 est « Mr. Villain's Day Off » et le rang 2000
 *       « Isekai Shihai no Skill Taker » — personne ne devine ca sur une page de chapitre.</li>
 * </ul>
 * D'ou un palier difficile qui s'arrete bien plus tot en manga. Au-dela du dernier rang, une
 * oeuvre reste au catalogue (autocompletion, validation des reponses) mais n'est jamais tiree.
 */
@Component
@ConfigurationProperties(prefix = "app.catalog.tiers")
public class CatalogTiers {

    @NestedConfigurationProperty
    private Ranks anime = new Ranks(150, 500, 1500);
    @NestedConfigurationProperty
    private Ranks manga = new Ranks(150, 450, 900);

    public static class Ranks {
        private int easyMaxRank;
        private int mediumMaxRank;
        private int hardMaxRank;

        public Ranks() {}

        public Ranks(int easyMaxRank, int mediumMaxRank, int hardMaxRank) {
            this.easyMaxRank = easyMaxRank;
            this.mediumMaxRank = mediumMaxRank;
            this.hardMaxRank = hardMaxRank;
        }

        public int getEasyMaxRank() { return easyMaxRank; }
        public void setEasyMaxRank(int easyMaxRank) { this.easyMaxRank = easyMaxRank; }

        public int getMediumMaxRank() { return mediumMaxRank; }
        public void setMediumMaxRank(int mediumMaxRank) { this.mediumMaxRank = mediumMaxRank; }

        public int getHardMaxRank() { return hardMaxRank; }
        public void setHardMaxRank(int hardMaxRank) { this.hardMaxRank = hardMaxRank; }
    }

    public Ranks forType(WorkType type) {
        return type == WorkType.ANIME ? anime : manga;
    }

    public Ranks getAnime() { return anime; }
    public void setAnime(Ranks anime) { this.anime = anime; }

    public Ranks getManga() { return manga; }
    public void setManga(Ranks manga) { this.manga = manga; }
}
