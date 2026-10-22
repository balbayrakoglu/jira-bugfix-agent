FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM maven:3.9-eclipse-temurin-21
RUN useradd --create-home agent \
    && mkdir -p /home/agent/.m2 /home/agent/work /home/agent/data \
    && chown -R agent:agent /home/agent
USER agent
WORKDIR /home/agent
COPY --from=build --chown=agent /src/target/jira-bugfix-agent-*.jar app.jar
ENV AGENT_WORK_DIR=/home/agent/work
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
