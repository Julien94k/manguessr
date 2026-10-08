FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml ./
# Couche de cache : les dependances ne sont retelechargees que si le pom change.
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Utilisateur dedie : une faille dans l'appli ne donne pas root dans le conteneur. Le dossier
# du cache lui appartient, pour qu'un volume neuf soit cree avec ces droits.
RUN addgroup -S manguessr && adduser -S -G manguessr manguessr \
    && mkdir -p /app/media-cache && chown manguessr:manguessr /app/media-cache
COPY --from=build /app/target/*.jar app.jar
USER manguessr

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
