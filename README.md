# ManGuessr

![CI](https://github.com/Julien94k/manguessr/actions/workflows/ci.yml/badge.svg)

Jeu de devinettes sur l'univers **anime** et **manga**, inspiré d'AniGuessr : un défi quotidien
par mode, identique pour tous les joueurs, plus un mode illimité. Application full-stack
**Java / Spring Boot + React / TypeScript**, conteneurisée avec Docker et auto-hébergée sur un
Raspberry Pi.

**Démo en ligne : <https://manguessr.julienhome.com>**

## Modes de jeu

| Mode | Univers | Principe |
|---|---|---|
| Images | Anime, Manga | 3 images de plus en plus parlantes, indice sur les initiales du titre |
| Personnages | Anime, Manga | 4 portraits de séries différentes : retrouver l'œuvre de chacun |
| Opening / Ending | Anime | Générique en audio, clip vidéo en indice |
| Anidle / Mangadle | Anime, Manga | Déduction par comparaison d'attributs, façon Wordle |
| Cover-deblur | Manga | Jaquette de moins en moins floutée, 10 essais |

Autour du jeu : comptes utilisateurs, série de victoires, classement, statistiques de profil et
partage du score.

## Stack technique

**Back-end** : Java 21, Spring Boot 3, Spring Security (authentification JWT), Spring Data JPA /
Hibernate, PostgreSQL 16, Bean Validation, cache applicatif, tâches planifiées et asynchrones.

**Front-end** : React 18, TypeScript, Vite, Tailwind CSS, Zustand, React Router, Axios.

**Infra** : Docker, Docker Compose, Nginx (reverse proxy, limitation de débit, en-têtes de
sécurité CSP / HSTS), GitHub Actions.

**Tests** : 263 tests JUnit 5 (unitaires et d'intégration MockMvc sur base H2).

## Architecture

```
navigateur ──► Nginx (front React statique + reverse proxy /api)
                 └──► API Spring Boot ──► PostgreSQL
                         ├── ingestion du catalogue : AniList (GraphQL), MangaDex, AnimeThemes
                         └── proxy médias signé (images retouchées en cache disque, flux audio/vidéo)
```

- **Moteur de parties extensible** : chaque mode implémente une interface `GameModeHandler`
  (œuvres tirables, vue client, validation d'une réponse) ; le cycle de vie et le calcul du score
  restent centralisés et **toujours recalculés côté serveur**.
- **Ingestion multi-sources** depuis des API publiques, avec limitation de débit par source et
  regroupement des saisons d'une même série.
- **Anti-triche** : les médias passent par un proxy à jetons signés, pour que ni l'URL ni le nom
  de fichier ne révèlent la réponse. Les images sont floutées ou recadrées côté serveur (flou
  séparable en plusieurs passes, peu coûteux sur un Raspberry Pi) puis mises en cache sur disque.
- **Performance** : préchauffage asynchrone des images dès la création d'une partie et du défi
  du jour (première image de ~0,9 s à quelques ms), mise en cache des modes disponibles
  (1,6 s → 6 ms sur la page d'accueil).
- **Sécurité** : JWT, contrôle de propriété sur chaque partie, rôles admin, limitation de débit
  et en-têtes de sécurité dans Nginx, conteneur back-end exécuté sans les droits root.

## Lancer le projet

Prérequis : Docker et Docker Compose.

```bash
cp .env.sample .env
# Renseigner au minimum :
#   JWT_SECRET   -> openssl rand -base64 48
#   DB_PASSWORD  -> openssl rand -hex 24
#   ADMIN_EMAILS -> email du compte qui pourra lancer l'ingestion du catalogue
docker compose up -d --build
```

L'application est servie sur <http://localhost:8091>. Au premier démarrage, créer le compte
admin, puis lancer l'ingestion du catalogue (`POST /api/catalog/ingestion` avec le JWT admin).

### Développement

```bash
docker compose up -d postgres              # base de données seule
cd backend && ./run-dev.sh                 # API sur :8080 (Java 21)
cd frontend && npm install && npm run dev  # front sur :3000, proxy /api -> :8080
```

### Tests

```bash
cd backend && mvn test                     # 263 tests, base H2 en mémoire
cd frontend && npm run lint && npm run build
```

## Données

Les métadonnées et les médias proviennent des API publiques
[AniList](https://anilist.co), [MangaDex](https://mangadex.org) et
[AnimeThemes](https://animethemes.moe). Ils restent la propriété de leurs ayants droit et ne
sont pas inclus dans ce dépôt.
