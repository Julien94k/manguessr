package com.manguessr.service.media;

import com.manguessr.config.GameProperties;
import com.manguessr.repository.MediaCharacterRepository;
import com.manguessr.repository.MediaImageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Sert les images du jeu apres traitement, sans jamais exposer leur URL d'origine.
 *
 * Deux garanties qui manquent a AniGuessr :
 * <ul>
 *   <li>le flou est applique <b>ici</b>, pas en CSS : sur le mode Cover-deblur, un
 *       {@code filter: blur()} se retirerait dans les devtools et donnerait la reponse</li>
 *   <li>le palier de flou est fixe par un jeton signe : le client ne peut pas reclamer
 *       une image nette alors que la manche n'en est qu'au premier essai</li>
 * </ul>
 *
 * Chaque rendu est mis en cache sur disque : sur Raspberry Pi, refaire le flou a chaque
 * requete serait couteux, et les paliers sont en nombre fini.
 */
@Service
public class MediaProxyService {

    private static final Logger log = LoggerFactory.getLogger(MediaProxyService.class);

    /** Largeur de service : au-dela, on gaspille de la bande passante sans gagner en jouabilite. */
    private static final int OUTPUT_WIDTH = 512;
    /** Le flou par boite etire les pixels de bord : on rogne la bordure qui en resulte. */
    private static final double BLUR_BORDER_TRIM = 0.04;
    /** Portion conservee lors d'un recadrage : assez large pour rester reconnaissable. */
    private static final double CROP_ZOOM = 0.45;
    private static final float JPEG_QUALITY_HINT = 0.82f;
    /**
     * Hauteur maximale d'un rendu, en multiple de sa largeur. Une page de manga (~1,45) passe ;
     * une bande de webtoon est ramenee a sa zone la plus riche.
     */
    private static final double MAX_HEIGHT_RATIO = 1.6;

    private final MediaImageRepository imageRepository;
    private final MediaCharacterRepository characterRepository;
    private final GameProperties gameProperties;
    private final HttpClient httpClient;
    private final Path cacheDirectory;

    /**
     * Verrous de rendu, repartis par cle de cache. Leur nombre est fixe et sans rapport avec
     * le nombre d'images : deux images distinctes peuvent partager un verrou, ce qui les
     * serialise sans jamais rien bloquer durablement.
     */
    private final Object[] renderLocks = new Object[64];

    public MediaProxyService(MediaImageRepository imageRepository,
                             MediaCharacterRepository characterRepository,
                             GameProperties gameProperties,
                             @Value("${app.media.cache-path:./media-cache}") String cachePath) {
        this.imageRepository = imageRepository;
        this.characterRepository = characterRepository;
        this.gameProperties = gameProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.cacheDirectory = Path.of(cachePath);

        Arrays.setAll(renderLocks, index -> new Object());

        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Cache media inaccessible : " + cacheDirectory, e);
        }
    }

    /**
     * Rend l'image designee par un jeton deja verifie.
     *
     * @return les octets JPEG prets a etre servis
     */
    public byte[] render(MediaToken token) {
        String key = cacheKey(token);
        Path cached = cacheDirectory.resolve(key);
        byte[] fromCache = readCached(cached);
        if (fromCache != null) {
            return fromCache;
        }

        // Un seul rendu a la fois par image : le prechauffage et la requete du joueur visent
        // souvent la meme, et sans ce verrou les deux telechargeraient puis redimensionneraient
        // en double. Verrous en nombre fixe : il n'y a rien a nettoyer ensuite.
        synchronized (renderLocks[Math.floorMod(key.hashCode(), renderLocks.length)]) {
            byte[] rendered = readCached(cached);
            return rendered != null ? rendered : renderAndCache(token, cached);
        }
    }

    /** @return les octets en cache, ou {@code null} si l'image n'y est pas (ou est illisible). */
    private byte[] readCached(Path cached) {
        if (!Files.exists(cached)) {
            return null;
        }
        try {
            return Files.readAllBytes(cached);
        } catch (IOException e) {
            // Cache illisible (disque plein, fichier tronque) : on regenere plutot que d'echouer.
            log.warn("Cache media illisible pour {}, regeneration : {}", cached, e.getMessage());
            return null;
        }
    }

    private byte[] renderAndCache(MediaToken token, Path cached) {
        BufferedImage source = download(resolveSourceUrl(token));
        BufferedImage rendered = applyTransform(source, token);
        byte[] bytes = toJpeg(rendered);

        try {
            // Ecriture puis renommage : une requete concurrente ne doit jamais lire un fichier partiel.
            Path temporary = Files.createTempFile(cacheDirectory, "render", ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Ecriture du cache media impossible : {}", e.getMessage());
        }

        return bytes;
    }

    /** Resout l'URL d'origine selon la table designee par le jeton. */
    private String resolveSourceUrl(MediaToken token) {
        return switch (token.source()) {
            case IMAGE -> imageRepository.findById(token.id())
                    .orElseThrow(() -> new IllegalArgumentException("Image introuvable."))
                    .getUrl();
            case CHARACTER -> characterRepository.findById(token.id())
                    .map(character -> character.getImageUrl())
                    .orElseThrow(() -> new IllegalArgumentException("Portrait introuvable."));
            case THEME_AUDIO, THEME_VIDEO -> throw new IllegalArgumentException("Ce jeton ne désigne pas une image.");
        };
    }

    private BufferedImage applyTransform(BufferedImage source, MediaToken token) {
        return switch (token.transform()) {
            // Redimensionnement d'abord : evaluer les fenetres d'une bande de webtoon a 512 px
            // de large coute bien moins qu'a sa resolution d'origine.
            case RAW -> ImageTransformer.limitTallness(
                    ImageTransformer.resizeToWidth(source, OUTPUT_WIDTH), MAX_HEIGHT_RATIO);

            case CROP -> ImageTransformer.resizeToWidth(
                    ImageTransformer.randomCrop(source, CROP_ZOOM, token.parameter()), OUTPUT_WIDTH);

            case BLUR -> {
                BufferedImage resized = ImageTransformer.resizeToWidth(source, OUTPUT_WIDTH);
                int radius = blurRadiusForStep(token.parameter());
                if (radius <= 0) {
                    yield resized;
                }
                yield ImageTransformer.trimBorder(
                        ImageTransformer.blur(resized, radius), BLUR_BORDER_TRIM);
            }
        };
    }

    /**
     * Rayon de flou du mode Cover-deblur : maximal au premier essai, nul une fois les
     * essais epuises ou la reponse trouvee.
     *
     * @param step nombre d'essais rates, de 0 a maxAttempts
     */
    public int blurRadiusForStep(int step) {
        GameProperties.CoverReveal config = gameProperties.getCoverReveal();
        int maxAttempts = config.getMaxAttempts();

        int clampedStep = Math.min(Math.max(step, 0), maxAttempts);
        double remaining = 1.0 - (double) clampedStep / maxAttempts;

        return (int) Math.round(config.getBaseBlurPx() * remaining);
    }

    /**
     * Referer a envoyer selon l'hote.
     *
     * AniList filtre les requetes sans Referer ; MangaDex fait l'inverse et, face a un
     * Referer etranger, repond {@code 200} avec une <b>image de remplacement</b> anti-hotlink
     * (logo « MangaDex », fond blanc) au lieu de la couverture. L'erreur est donc silencieuse :
     * seul le contenu de l'image la trahit. D'ou un Referer reserve aux hotes AniList.
     */
    static Optional<String> refererFor(String url) {
        String host = URI.create(url).getHost();
        if (host != null && (host.equals("anilist.co") || host.endsWith(".anilist.co"))) {
            return Optional.of("https://anilist.co/");
        }
        return Optional.empty();
    }

    private BufferedImage download(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "manguessr/1.0")
                    .GET();
            refererFor(url).ifPresent(referer -> builder.header("Referer", referer));
            HttpRequest request = builder.build();

            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Source media indisponible (" + response.statusCode() + ")");
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                throw new IllegalStateException("Format d'image non reconnu.");
            }
            return toRgb(image);

        } catch (IOException e) {
            throw new IllegalStateException("Telechargement du media impossible", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telechargement du media interrompu", e);
        }
    }

    /** Le JPEG ne gere pas la transparence : on aplatit les PNG sur un fond opaque. */
    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage converted =
                new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = converted.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return converted;
    }

    private byte[] toJpeg(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY_HINT);

            try (javax.imageio.stream.ImageOutputStream stream =
                         ImageIO.createImageOutputStream(output)) {
                writer.setOutput(stream);
                writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }

            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Encodage JPEG impossible", e);
        }
    }

    private String cacheKey(MediaToken token) {
        return token.source().name().toLowerCase() + "-" + token.id() + "-"
                + token.transform().name().toLowerCase() + "-" + token.parameter() + ".jpg";
    }
}
