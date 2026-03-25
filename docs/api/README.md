# API-Spezifikation (OpenAPI)

Die versionierte OpenAPI-Spezifikation wird in CI erzeugt und als Artefakt abgelegt.

## Erwartetes Artefakt
- `openapi-v<version>.yaml`

Beispiel:
- `openapi-v0.0.1-SNAPSHOT.yaml`

## Lokal erzeugen

```bash
cd backend
./mvnw -q -DskipTests package
VERSION=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)
java -jar target/backend-${VERSION}.jar > /tmp/backend.log 2>&1 &
sleep 10
curl -fsS http://127.0.0.1:8080/v3/api-docs.yaml -o ../docs/api/openapi-v${VERSION}.yaml
pkill -f "backend-${VERSION}.jar" || true
```

Damit kann das Frontend gegen eine eindeutig versionierte API-Spezifikation entwickeln.

