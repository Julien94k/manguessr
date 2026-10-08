package com.manguessr.service.media;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le flou est le coeur du mode Cover-deblur : s'il n'attenue pas reellement l'image,
 * la manche est donnee des le premier essai.
 */
class ImageTransformerTest {

    /** Damier a fort contraste : le flou doit visiblement reduire l'ecart entre cases. */
    private BufferedImage checkerboard(int size, int cell) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        for (int y = 0; y < size; y += cell) {
            for (int x = 0; x < size; x += cell) {
                graphics.setColor(((x / cell) + (y / cell)) % 2 == 0 ? Color.BLACK : Color.WHITE);
                graphics.fillRect(x, y, cell, cell);
            }
        }
        graphics.dispose();
        return image;
    }

    /** Image a moitie blanche, a moitie illustree (bruit colore) : le cas d'une couverture a marge. */
    private BufferedImage halfBlankCover(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(42);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, x < size / 2 ? 0xFFFFFF : random.nextInt(0x1000000));
            }
        }
        return image;
    }

    private BufferedImage filled(int size, java.util.function.IntBinaryOperator pixel) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setRGB(x, y, pixel.applyAsInt(x, y));
            }
        }
        return image;
    }

    @Test
    void une_bande_de_webtoon_est_ramenee_a_sa_zone_dessinee() {
        // Bande 100 x 1000, blanche sauf une case dessinee entre 600 et 760 px.
        java.util.Random noise = new java.util.Random(7);
        BufferedImage strip = new BufferedImage(100, 1000, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 1000; y++) {
            for (int x = 0; x < 100; x++) {
                strip.setRGB(x, y, y >= 600 && y < 760 ? noise.nextInt(0x1000000) : 0xFFFFFF);
            }
        }

        BufferedImage slice = ImageTransformer.limitTallness(strip, 1.6);

        assertThat(slice.getWidth()).isEqualTo(100);
        assertThat(slice.getHeight()).isEqualTo(160);
        long white = 0;
        for (int y = 0; y < slice.getHeight(); y++) {
            for (int x = 0; x < slice.getWidth(); x++) {
                if ((slice.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF) {
                    white++;
                }
            }
        }
        assertThat((double) white / (100 * 160)).as("part de blanc dans la fenetre").isLessThan(0.5);
    }

    @Test
    void une_page_de_manga_ordinaire_n_est_pas_decoupee() {
        BufferedImage page = new BufferedImage(512, 734, BufferedImage.TYPE_INT_RGB);

        assertThat(ImageTransformer.limitTallness(page, 1.6)).isSameAs(page);
    }

    @Test
    void le_score_de_detail_prefere_l_illustration_au_texte_et_au_blanc() {
        BufferedImage blank = filled(96, (x, y) -> 0xFFFFFF);
        // Texte simule : traits noirs clairsemes sur fond blanc.
        BufferedImage text = filled(96, (x, y) -> (y % 12 < 3 && x % 7 < 4) ? 0x000000 : 0xFFFFFF);
        BufferedImage illustration = filled(96, (x, y) -> Color.HSBtoRGB((x + y) / 96f, 0.8f, 0.4f + (x % 16) / 32f));

        double blankScore = ImageTransformer.detailScore(blank, 0, 0, 96, 96);
        double textScore = ImageTransformer.detailScore(text, 0, 0, 96, 96);
        double illustrationScore = ImageTransformer.detailScore(illustration, 0, 0, 96, 96);

        assertThat(blankScore).isZero();
        assertThat(illustrationScore).isGreaterThan(textScore);
        assertThat(textScore).isGreaterThan(blankScore);
    }

    @Test
    void le_recadrage_evite_les_marges_blanches() {
        BufferedImage source = halfBlankCover(400);

        // Regression : un tirage purement aleatoire servait des zones blanches, indevinables.
        for (int seed = 0; seed < 10; seed++) {
            BufferedImage cropped = ImageTransformer.randomCrop(source, 0.45, seed);
            long white = 0;
            for (int y = 0; y < cropped.getHeight(); y++) {
                for (int x = 0; x < cropped.getWidth(); x++) {
                    if ((cropped.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF) {
                        white++;
                    }
                }
            }
            double whiteRatio = (double) white / (cropped.getWidth() * cropped.getHeight());
            assertThat(whiteRatio).as("graine %d", seed).isLessThan(0.5);
        }
    }

    @Test
    void le_recadrage_n_inclut_jamais_le_bandeau_titre_ni_le_pied_de_couverture() {
        // Couverture 400x600 : titre rouge vif en haut, auteur jaune en bas, illustration terne
        // au milieu. Le score de detail preferait les logos : la bande doit l'en empecher.
        int height = 600;
        int titleEnd = (int) (height * ImageTransformer.TITLE_BAND_TOP);
        int footerStart = (int) (height * ImageTransformer.TITLE_BAND_BOTTOM);
        java.util.Random noise = new java.util.Random(3);
        BufferedImage cover = new BufferedImage(400, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 400; x++) {
                int pixel;
                if (y < titleEnd) {
                    pixel = (x / 10 + y / 10) % 2 == 0 ? 0xFF0000 : 0x0000FF;
                } else if (y >= footerStart) {
                    pixel = (x / 10) % 2 == 0 ? 0xFFFF00 : 0x000000;
                } else {
                    int grey = 100 + noise.nextInt(30);
                    pixel = (grey << 16) | (grey << 8) | grey;
                }
                cover.setRGB(x, y, pixel);
            }
        }

        for (int seed = 0; seed < 20; seed++) {
            BufferedImage cropped = ImageTransformer.randomCrop(cover, 0.45, seed);
            for (int y = 0; y < cropped.getHeight(); y++) {
                for (int x = 0; x < cropped.getWidth(); x++) {
                    int rgb = cropped.getRGB(x, y) & 0xFFFFFF;
                    assertThat(rgb == 0xFF0000 || rgb == 0x0000FF || rgb == 0xFFFF00)
                            .as("graine %d : pixel de titre ou de pied en (%d, %d)", seed, x, y)
                            .isFalse();
                }
            }
        }
    }

    /** Ecart-type des luminances : mesure objective du contraste restant. */
    private double contrast(BufferedImage image) {
        long sum = 0;
        long count = (long) image.getWidth() * image.getHeight();
        int[] values = new int[(int) count];
        int index = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int luminance = image.getRGB(x, y) & 0xFF;
                values[index++] = luminance;
                sum += luminance;
            }
        }

        double mean = (double) sum / count;
        double variance = 0;
        for (int value : values) {
            variance += (value - mean) * (value - mean);
        }
        return Math.sqrt(variance / count);
    }

    @Test
    void le_flou_reduit_fortement_le_contraste() {
        BufferedImage source = checkerboard(128, 8);
        double before = contrast(source);
        double after = contrast(ImageTransformer.blur(source, 16));

        assertThat(before).isGreaterThan(100);
        // Un damier flou a 16 px doit devenir quasiment uniforme.
        assertThat(after).isLessThan(before * 0.1);
    }

    @Test
    void un_rayon_plus_grand_floute_davantage() {
        BufferedImage source = checkerboard(128, 8);

        double light = contrast(ImageTransformer.blur(source, 2));
        double heavy = contrast(ImageTransformer.blur(source, 12));

        assertThat(heavy).isLessThan(light);
    }

    @Test
    void un_rayon_nul_laisse_l_image_intacte() {
        BufferedImage source = checkerboard(64, 8);
        assertThat(ImageTransformer.blur(source, 0)).isSameAs(source);
    }

    @Test
    void le_flou_preserve_les_dimensions() {
        BufferedImage blurred = ImageTransformer.blur(checkerboard(128, 8), 10);

        assertThat(blurred.getWidth()).isEqualTo(128);
        assertThat(blurred.getHeight()).isEqualTo(128);
    }

    @Test
    void le_flou_n_assombrit_pas_les_bords() {
        // Image uniformement grise : apres flou, elle doit rester grise partout,
        // y compris sur les bords. Un traitement naif y ferait apparaitre du noir.
        BufferedImage uniform = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = uniform.createGraphics();
        graphics.setColor(new Color(128, 128, 128));
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();

        BufferedImage blurred = ImageTransformer.blur(uniform, 12);

        for (int x = 0; x < 64; x++) {
            assertThat(blurred.getRGB(x, 0) & 0xFF)
                    .as("bord superieur en x=%d", x)
                    .isBetween(125, 131);
            assertThat(blurred.getRGB(0, x) & 0xFF)
                    .as("bord gauche en y=%d", x)
                    .isBetween(125, 131);
        }
    }

    @Test
    void le_redimensionnement_preserve_le_rapport_et_n_agrandit_jamais() {
        BufferedImage wide = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);

        BufferedImage resized = ImageTransformer.resizeToWidth(wide, 512);
        assertThat(resized.getWidth()).isEqualTo(512);
        assertThat(resized.getHeight()).isEqualTo(256);

        BufferedImage small = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        assertThat(ImageTransformer.resizeToWidth(small, 512)).isSameAs(small);
    }

    @Test
    void le_recadrage_est_reproductible_pour_une_meme_graine() {
        BufferedImage source = checkerboard(200, 10);

        BufferedImage first = ImageTransformer.randomCrop(source, 0.45, 1234);
        BufferedImage second = ImageTransformer.randomCrop(source, 0.45, 1234);

        assertThat(first.getWidth()).isEqualTo(second.getWidth());
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                assertThat(first.getRGB(x, y)).isEqualTo(second.getRGB(x, y));
            }
        }
    }

    @Test
    void le_recadrage_reduit_bien_la_zone_visible() {
        BufferedImage source = checkerboard(200, 10);
        BufferedImage cropped = ImageTransformer.randomCrop(source, 0.45, 7);

        assertThat(cropped.getWidth()).isEqualTo(90);
        assertThat(cropped.getHeight()).isEqualTo(90);
    }

    @Test
    void des_graines_differentes_donnent_des_cadrages_differents() {
        BufferedImage source = checkerboard(200, 10);

        boolean anyDifference = false;
        for (int seed = 1; seed <= 20 && !anyDifference; seed++) {
            BufferedImage a = ImageTransformer.randomCrop(source, 0.45, 1);
            BufferedImage b = ImageTransformer.randomCrop(source, 0.45, seed);
            for (int y = 0; y < a.getHeight() && !anyDifference; y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    if (a.getRGB(x, y) != b.getRGB(x, y)) {
                        anyDifference = true;
                        break;
                    }
                }
            }
        }
        assertThat(anyDifference).isTrue();
    }

    @Test
    void le_rognage_de_bordure_retire_bien_la_marge() {
        BufferedImage trimmed = ImageTransformer.trimBorder(checkerboard(100, 10), 0.04);

        assertThat(trimmed.getWidth()).isEqualTo(92);
        assertThat(trimmed.getHeight()).isEqualTo(92);
    }
}
