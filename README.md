# CodeScope Backend

CodeScope backend is a Spring Boot 3.x reactive application for project-based code review, AI analysis, reporting, notifications, and realtime updates.

## Current Architecture

- Framework: Spring Boot WebFlux
- Language: Java 17
- Persistence: Reactive MongoDB
- Cache / messaging support: Optional Redis, disabled by default
- Auth: JWT + Spring Security
- File storage: Cloudinary, with local-path fallback support in some flows
- AI providers: OpenAI, Hugging Face, Ollama, plus provider fallback through the app's LLM client
- Realtime: Native WebFlux WebSocket on `/ws`

## Primary API Areas

- `/api/auth/*`: register, login, current user
- `/api/projects/*`: project CRUD and primary file-management flow
- `/api/projects/{projectId}/analysis/*`: analysis job creation and status
- `/api/chat/*`: project chat history, send, stream, clear
- `/api/reports/*`: report creation and retrieval
- `/api/export/*`: export/download endpoints
- `/api/dashboard`: dashboard metrics
- `/api/notifications/*`: notification management
- `/api/users/*`: profile and account actions

## Project And File Flows

The preferred project/file API is the `/api/projects/*` surface.

Legacy compatibility endpoints still exist under `/api/upload/*`:

- `/api/upload/project`
- `/api/upload/file`

These remain available so older clients do not break, but new work should use `/api/projects/*`.

## Project Identity

Projects have:

- MongoDB `_id`: internal document identifier
- `projectId`: public API identifier

The backend now prefers `projectId` in ownership and lookup checks, while keeping `_id` fallback for older records and migration safety.

## WebSocket Protocol

The backend does not use SockJS/STOMP.

It uses native JSON-over-WebSocket:

- Endpoint: `/ws`
- Authentication: `?token=<JWT>`
- Subscribe message:

```json
{ "type": "SUBSCRIBE", "topic": "/topic/notifications" }
```

- Server envelope:

```json
{ "topic": "/topic/notifications", "payload": { } }
```

## Environment Notes

Important config lives in:

- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`
- `.env`

Common values include:

- `MONGODB_URI`
- `JWT_SECRET`
- `CLOUDINARY_URL`
- `OPENAI_API_KEY`
- `HUGGINGFACE_API_KEY`
- `FRONTEND_ORIGIN` / `FRONTEND_ORIGINS`
- `APP_REDIS_ENABLED`
- `PROJECT_ID_BACKFILL_ENABLED`
- `REDIS_URL` when Redis is explicitly enabled

## Local Development

Run with Maven:

```bash
./mvnw spring-boot:run
```

Frontend dev default:

- Frontend origin: `http://localhost:4200`
- Backend API: `http://localhost:8080/api`
- Backend WebSocket: `ws://localhost:8080/ws?token=JWT`

Recommended production defaults:

- `SPRING_PROFILES_ACTIVE=prod`
- `APP_REDIS_ENABLED=false`
- `PROJECT_ID_BACKFILL_ENABLED=false`
- Set `CLOUDINARY_URL`, `MONGODB_URI`, `JWT_SECRET`, and frontend origin variables explicitly on the host

## Notes

- The app is reactive-first and MongoDB-first; old JPA/PostgreSQL wiring has been removed.
- WebSocket support is native WebFlux and aligned with the Angular frontend websocket service.
- Documentation should treat `/api/projects/*` as the source of truth for project/file operations.
