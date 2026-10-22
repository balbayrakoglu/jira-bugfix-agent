# jira-bugfix-agent

The agent service. See the [project README](../README.md) for the full architecture, the AI
integration design, and setup instructions — this module is one of three pieces in that lab.

All tunables (confidence threshold, clone depth, context size, concurrency, verification command,
output language, …) live under `agent.*` in
[`src/main/resources/application.yml`](src/main/resources/application.yml), each documented inline
in the [project README's configuration notes](../README.md#project-structure).

## Running standalone

```bash
mvn clean verify
set -a && source ../.env && set +a
java -jar target/jira-bugfix-agent-0.1.0-SNAPSHOT.jar
```

Or via Docker:

```bash
mvn -DskipTests package
docker build -t jira-bugfix-agent .
docker run --env-file ../.env -p 8080:8080 jira-bugfix-agent
```

## Admin API (`X-Admin-Token` header required)

```bash
curl -H "X-Admin-Token: $AGENT_ADMIN_TOKEN" localhost:8080/api/bugs
curl -H "X-Admin-Token: $AGENT_ADMIN_TOKEN" localhost:8080/api/bugs/SHOP-123
curl -X POST -H "X-Admin-Token: $AGENT_ADMIN_TOKEN" localhost:8080/api/bugs/SHOP-123/process
```
