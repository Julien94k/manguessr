package com.manguessr.service.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Operations d'image pures, sans dependance a Spring ni au reseau : facilement testables.
 *
 * Le flou est un flou par boite applique trois fois. C'est l'approximation classique d'un
 * flou gaussien, separable en deux passes lineaires : le cout est proportionnel au nombre
 * de pixels et non au carre du rayon, ce qui compte sur un Raspberry Pi ou un rayon de 32 px
 * en convolution directe couterait 65 fois plus cher.
 */
public final class ImageTransformer {

    /** Trois passes de boite approchent une gaussienne de facon visuellement indistinguable. */
    private static final int BOX_BLUR_PASSES = 3;

    private ImageTransformer() {}

    /** Redimensionne en preservant le rapport, sans jamais agrandir. */
    public static BufferedImage resizeToWidth(BufferedImage source, int targetWidth) {
        if (source.getWidth() <= targetWidth) {
            return source;
        }
        int targetHeight = Math.max(1, source.getHeight() * targetWidth / source.getWidth());

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();

        return resized;
    }

    /**
     * Flou par boite, applique {@value #BOX_BLUR_PASSES} fois.
     *
     * @param radius rayon en pixels ; un rayon nul renvoie l'image inchangee
     */
    public static BufferedImage blur(BufferedImage source, int radius) {
        if (radius <= 0) {
            return source;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        int[] scratch = new int[pixels.length];

        for (int pass = 0; pass < BOX_BLUR_PASSES; pass++) {
            boxBlurHorizontal(pixels, scratch, width, height, radius);
            boxBlurHorizontal(scratch, pixels, height, width, radius);
        }

        BufferedImage blurred = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        blurred.setRGB(0, 0, width, height, pixels, 0, width);
        return blurred;
    }

    /**
     * Moyenne glissante sur les lignes, en ecrivant le resultat <b>transpose</b>.
     *
     * La transposition permet de reutiliser exactement la meme passe pour les colonnes :
     * deux appels successifs donnent un flou bidirectionnel et remettent l'image a l'endroit.
     */
    private static void boxBlurHorizontal(int[] source, int[] target, int width, int height, int radius) {
        int windowSize = radius + radius + 1;

        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            int red = 0;
            int green = 0;
            int blue = 0;

            // Amorcage : les pixels hors image sont remplaces par le pixel de bord,
            // sinon les bords s'assombriraient visiblement.
            for (int i = -radius; i <= radius; i++) {
                int pixel = source[rowStart + clamp(i, width)];
                red += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                blue += pixel & 0xFF;
            }

            for (int x = 0; x < width; x++) {
                target[x * height + y] =
                        ((red / windowSize) << 16) | ((green / windowSize) << 8) | (blue / windowSize);

                int leaving = source[rowStart + clamp(x - radius, width)];
                int entering = source[rowStart + clamp(x + radius + 1, width)];

                red += ((entering >> 16) & 0xFF) - ((leaving >> 16) & 0xFF);
                green += ((entering >> 8) & 0xFF) - ((leaving >> 8) & 0xFF);
                blue += (entering & 0xFF) - (leaving & 0xFF);
            }
        }
    }

    private static int clamp(int index, int size) {
        if (index < 0) {
            return 0;
        }
        return index >= size ? size - 1 : index;
    }

    /**
     * Rogne les bords d'un pourcentage donne.
     *
     * Sert apres un flou : le flou par boite etire les pixels de bord, ce qui laisse une
     * bordure d'aspect different du reste.
     */
    public static BufferedImage trimBorder(BufferedImage source, double percent) {
        int marginX = (int) (source.getWidth() * percent);
        int marginY = (int) (source.getHeight() * percent);

        int width = source.getWidth() - marginX * 2;
        int height = source.getHeight() - marginY * 2;
        if (width <= 0 || height <= 0) {
            return source;
        }

        return source.getSubimage(marginX, marginY, width, height);
    }

    /**
     * Recadre une region aleatoire mais reproductible, determinee par la graine.
     *
     * Les couvertures de volumes manga portent le titre imprime : ne montrer qu'une portion
     * zoomee evite de donner la reponse. C'est l'equivalent du « videos are cropped and
     * blurred to hide obvious clues (like titles) » d'AniGuessr.
     *
     * @param zoom     fraction de l'image conservee, entre 0 et 1
     * @param seed     graine, typiquement l'identifiant de la manche
     */
    public static BufferedImage randomCrop(BufferedImage source, double zoom, int seed) {
        double clampedZoom = Math.min(1.0, Math.max(0.1, zoom));

        int cropWidth = Math.max(1, (int) (source.getWidth() * clampedZoom));
        int cropHeight = Math.max(1, (int) (source.getHeight() * clampedZoom));

        // Plusieurs zones tirees, la plus riche retenue : une zone purement aleatoire tombe
        // regulierement sur une marge blanche ou un aplat, impossible a reconnaitre.
        Random random = new Random(seed);
        int bestX = 0;
        int bestY = 0;
        double bestScore = -1;

        // Bande verticale autorisee : le titre d'une couverture est presque toujours en haut,
        // l'auteur et le numero de volume en bas. Sans cette bande, le score de detail attire
        // precisement ces logos, contrastes et satures — et la zone retenue donne la reponse.
        int minY = (int) (source.getHeight() * TITLE_BAND_TOP);
        int maxY = (int) (source.getHeight() * TITLE_BAND_BOTTOM) - cropHeight;
        if (maxY < minY) {
            // Zoom trop large pour tenir dans la bande : on centre au mieux dans l'image.
            minY = Math.max(0, Math.min(minY, source.getHeight() - cropHeight));
            maxY = minY;
        }

        for (int candidate = 0; candidate < CROP_CANDIDATES; candidate++) {
            int x = source.getWidth() == cropWidth ? 0 : random.nextInt(source.getWidth() - cropWidth + 1);
            int y = minY + random.nextInt(maxY - minY + 1);

            double score = detailScore(source, x, y, cropWidth, cropHeight);
            if (score > bestScore) {
                bestScore = score;
                bestX = x;
                bestY = y;
            }
        }

        return source.getSubimage(bestX, bestY, cropWidth, cropHeight);
    }

    /** Haut de la bande de recadrage, en fraction de la hauteur : exclut le bandeau titre. */
    static final double TITLE_BAND_TOP = 0.22;
    /** Bas de la bande : exclut le nom de l'auteur et le numero de volume. */
    static final double TITLE_BAND_BOTTOM = 0.90;

    /** Fenetres evaluees sur une image trop haute, regulierement espacees de haut en bas. */
    private static final int TALL_SLICE_CANDIDATES = 12;

    /**
     * Ramene une image tres haute a une fenetre de rapport hauteur/largeur {@code maxRatio},
     * placee sur la zone la plus riche en dessin.
     *
     * Les chapitres de webtoon (manhwa) sont publies en bandes verticales de 512 x 7000 px :
     * reduites dans le cadre du jeu, elles deviennent illisibles. Une page de manga classique
     * (rapport ~1,45) passe inchangee. Deterministe : sans graine, le meme rendu a chaque fois.
     *
     * @return l'image elle-meme si elle n'est pas trop haute
     */
    public static BufferedImage limitTallness(BufferedImage source, double maxRatio) {
        int width = source.getWidth();
        int height = source.getHeight();
        int windowHeight = Math.max(1, (int) Math.round(width * maxRatio));
        if (height <= windowHeight) {
            return source;
        }

        int bestY = 0;
        double bestScore = -1;
        for (int candidate = 0; candidate < TALL_SLICE_CANDIDATES; candidate++) {
            int y = (int) ((long) candidate * (height - windowHeight) / (TALL_SLICE_CANDIDATES - 1));
            double score = detailScore(source, 0, y, width, windowHeight);
            if (score > bestScore) {
                bestScore = score;
                bestY = y;
            }
        }
        return source.getSubimage(0, bestY, width, windowHeight);
    }

    /** Zones evaluees par recadrage : au-dela, le gain est negligeable face au cout sur un Pi. */
    private static final int CROP_CANDIDATES = 8;
    /** Grille d'echantillonnage de l'evaluation : 576 points suffisent a juger une zone. */
    private static final int DETAIL_GRID = 24;
    /** Poids plancher de la saturation : un dessin en noir et blanc garde une chance. */
    private static final double SATURATION_FLOOR = 0.35;

    /**
     * Richesse visuelle d'une zone : ecart-type de la luminance, pondere par la saturation.
     *
     * L'ecart-type seul favoriserait du texte noir sur fond blanc — justement ce que le
     * recadrage veut eviter sur une couverture. La ponderation par la saturation fait passer
     * l'illustration devant.
     */
    static double detailScore(BufferedImage image, int x, int y, int width, int height) {
        double sum = 0;
        double sumOfSquares = 0;
        double saturation = 0;
        int samples = DETAIL_GRID * DETAIL_GRID;

        for (int gy = 0; gy < DETAIL_GRID; gy++) {
            int py = y + (int) ((gy + 0.5) * height / DETAIL_GRID);
            for (int gx = 0; gx < DETAIL_GRID; gx++) {
                int px = x + (int) ((gx + 0.5) * width / DETAIL_GRID);
                int rgb = image.getRGB(px, py);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
                sum += luminance;
                sumOfSquares += luminance * luminance;

                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                saturation += max == 0 ? 0 : (max - min) / (double) max;
            }
        }

        double mean = sum / samples;
        double deviation = Math.sqrt(Math.max(0, sumOfSquares / samples - mean * mean));
        return deviation * (SATURATION_FLOOR + saturation / samples);
    }
}
