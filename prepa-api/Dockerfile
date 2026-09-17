# Construction en deux temps : le jar est bati dans une image qui contient Maven,
# l'image finale ne porte qu'un environnement d'execution.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Les dependances changent rarement : les resoudre avant de copier les sources
# laisse cette couche en cache d'une construction a l'autre.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# L'application ne tourne pas en root : une faille dans une dependance ne donnerait
# alors pas les pleins pouvoirs sur le conteneur.
RUN addgroup -S prepa && adduser -S prepa -G prepa
USER prepa

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
