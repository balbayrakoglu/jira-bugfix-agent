FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --create-home minijira \
    && mkdir -p /home/minijira/data \
    && chown -R minijira:minijira /home/minijira
USER minijira
WORKDIR /home/minijira
COPY --from=build --chown=minijira /src/target/mini-jira-*.jar app.jar
ENV MINIJIRA_DB_URL=jdbc:h2:file:/home/minijira/data/mini-jira
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
