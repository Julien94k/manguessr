package com.manguessr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * Baremes de jeu, transcription exacte des rulebooks AniGuessr.
 *
 * Centralises ici et lus depuis {@code application.yml} : aucune valeur de score ne doit
 * etre ecrite en dur dans le code, sous peine de divergence entre le calcul et l'affichage.
 */
@Component
@ConfigurationProperties(prefix = "app.game")
public class GameProperties {

    @NestedConfigurationProperty
    private Images images = new Images();
    @NestedConfigurationProperty
    private Characters characters = new Characters();
    @NestedConfigurationProperty
    private Theme theme = new Theme();
    @NestedConfigurationProperty
    private Wordle wordle = new Wordle();
    @NestedConfigurationProperty
    private CoverReveal coverReveal = new CoverReveal();

    /** « You can earn up to 10 000 points per round / 2500 points are deducted per clue used ». */
    public static class Images {
        private int maxScore = 10_000;
        private int clueCost = 2_500;
        /** Image 1 offerte, puis image 2, image 3 et l'indice titre. */
        private int totalClues = 4;

        public int getMaxScore() { return maxScore; }
        public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
        public int getClueCost() { return clueCost; }
        public void setClueCost(int clueCost) { this.clueCost = clueCost; }
        public int getTotalClues() { return totalClues; }
        public void setTotalClues(int totalClues) { this.totalClues = totalClues; }
    }

    /** « 2000 points per correct character / 500 points per correct anime title », 4 par manche. */
    public static class Characters {
        private int characterPoints = 2_000;
        private int titlePoints = 500;
        private int perRound = 4;
        /**
         * Portrait tire parmi les N personnages les plus populaires de l'oeuvre, selon le palier
         * de l'oeuvre : une serie tres connue a beaucoup de personnages reconnaissables, une
         * oeuvre confidentielle guere plus que ses heros.
         */
        private int pickFromTopEasy = 10;
        private int pickFromTopMedium = 8;
        private int pickFromTopHard = 5;
        /** Au-dela de N oeuvres, une fiche AniList est un artefact et non un personnage. */
        private int genericMinWorks = 30;

        public int getCharacterPoints() { return characterPoints; }
        public void setCharacterPoints(int characterPoints) { this.characterPoints = characterPoints; }
        public int getTitlePoints() { return titlePoints; }
        public void setTitlePoints(int titlePoints) { this.titlePoints = titlePoints; }
        public int getPerRound() { return perRound; }
        public void setPerRound(int perRound) { this.perRound = perRound; }
        public int getPickFromTopEasy() { return pickFromTopEasy; }
        public void setPickFromTopEasy(int pickFromTopEasy) { this.pickFromTopEasy = pickFromTopEasy; }
        public int getPickFromTopMedium() { return pickFromTopMedium; }
        public void setPickFromTopMedium(int pickFromTopMedium) { this.pickFromTopMedium = pickFromTopMedium; }
        public int getPickFromTopHard() { return pickFromTopHard; }
        public void setPickFromTopHard(int pickFromTopHard) { this.pickFromTopHard = pickFromTopHard; }

        /** Profondeur de tirage pour une oeuvre de ce palier ; sans palier, la plus etroite. */
        public int pickFromTop(com.manguessr.model.enums.Difficulty tier) {
            if (tier == null) {
                return pickFromTopHard;
            }
            return switch (tier) {
                case EASY -> pickFromTopEasy;
                case MEDIUM -> pickFromTopMedium;
                case HARD -> pickFromTopHard;
            };
        }
        public int getGenericMinWorks() { return genericMinWorks; }
        public void setGenericMinWorks(int genericMinWorks) { this.genericMinWorks = genericMinWorks; }
    }

    /** « up to 5 000 points per round / 2 500 points are deducted if you use the video clue ». */
    public static class Theme {
        private int maxScore = 5_000;
        private int videoClueCost = 2_500;

        public int getMaxScore() { return maxScore; }
        public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
        public int getVideoClueCost() { return videoClueCost; }
        public void setVideoClueCost(int videoClueCost) { this.videoClueCost = videoClueCost; }
    }

    /** « After your 3rd guess, 500 points are deducted by wrong answer », 22 essais. */
    public static class Wordle {
        private int maxScore = 10_000;
        private int maxAttempts = 22;
        /** Les 3 premiers essais ne coutent rien. */
        private int freeAttempts = 3;
        private int wrongAnswerCost = 500;
        private int coverClueAfter = 12;
        private int synopsisClueAfter = 18;
        private int characterClueAfter = 21;

        public int getMaxScore() { return maxScore; }
        public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public int getFreeAttempts() { return freeAttempts; }
        public void setFreeAttempts(int freeAttempts) { this.freeAttempts = freeAttempts; }
        public int getWrongAnswerCost() { return wrongAnswerCost; }
        public void setWrongAnswerCost(int wrongAnswerCost) { this.wrongAnswerCost = wrongAnswerCost; }
        public int getCoverClueAfter() { return coverClueAfter; }
        public void setCoverClueAfter(int coverClueAfter) { this.coverClueAfter = coverClueAfter; }
        public int getSynopsisClueAfter() { return synopsisClueAfter; }
        public void setSynopsisClueAfter(int synopsisClueAfter) { this.synopsisClueAfter = synopsisClueAfter; }
        public int getCharacterClueAfter() { return characterClueAfter; }
        public void setCharacterClueAfter(int characterClueAfter) { this.characterClueAfter = characterClueAfter; }
    }

    /** Mode inedit : la jaquette se defloute a chaque essai rate. */
    public static class CoverReveal {
        private int maxScore = 10_000;
        private int maxAttempts = 10;
        private int wrongAnswerCost = 1_000;
        /** Rayon de flou au premier essai, sur une image de 512 px. */
        private int baseBlurPx = 32;

        public int getMaxScore() { return maxScore; }
        public void setMaxScore(int maxScore) { this.maxScore = maxScore; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public int getWrongAnswerCost() { return wrongAnswerCost; }
        public void setWrongAnswerCost(int wrongAnswerCost) { this.wrongAnswerCost = wrongAnswerCost; }
        public int getBaseBlurPx() { return baseBlurPx; }
        public void setBaseBlurPx(int baseBlurPx) { this.baseBlurPx = baseBlurPx; }
    }

    public Images getImages() { return images; }
    public void setImages(Images images) { this.images = images; }
    public Characters getCharacters() { return characters; }
    public void setCharacters(Characters characters) { this.characters = characters; }
    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme; }
    public Wordle getWordle() { return wordle; }
    public void setWordle(Wordle wordle) { this.wordle = wordle; }
    public CoverReveal getCoverReveal() { return coverReveal; }
    public void setCoverReveal(CoverReveal coverReveal) { this.coverReveal = coverReveal; }
}
