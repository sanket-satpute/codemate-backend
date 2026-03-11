# Phase 5: Upload + Project ID Migration (Reactive Mongo Path)

## Goal
- Remove Firebase dependency from active upload APIs.
- Stabilize project lookup behavior across `id` vs `projectId` during migration.
- Keep API responses consistent (`BaseResponse<T>` remains unchanged).

## Implemented Changes

1. Upload path moved off Firebase
- File: `src/main/java/com/codescope/backend/upload/controller/UploadController.java`
- Removed `FirebaseService` usage from:
  - `POST /api/upload/project`
  - `POST /api/upload/file`
- Replaced with reactive Mongo operations via `ProjectRepository`.
- Project metadata updates (`files`, `lastCorrectedAt`, `lastCorrectedByModel`, `lastCorrectionSummary`) now persist through Mongo.

2. Project ID compatibility during transition
- Updated project lookup call sites to support both identifiers:
  - `findByProjectId(...)`
  - fallback to `findById(...)`
- Files:
  - `src/main/java/com/codescope/backend/analysisjob/service/AnalysisJobService.java`
  - `src/main/java/com/codescope/backend/chat/ChatService.java`
  - `src/main/java/com/codescope/backend/ai/AIProcessingService.java`
  - `src/main/java/com/codescope/backend/upload/service/FileStorageService.java`
  - `src/main/java/com/codescope/backend/upload/controller/UploadController.java`

3. Project service contract alignment
- File: `src/main/java/com/codescope/backend/project/service/ProjectService.java`
- `ProjectResponse.id` now maps to `projectId` when present, else `id`.
- Owner-checked reads/updates/deletes support both ID styles:
  - primary: `findByIdAndOwnerId(...)`
  - fallback: `findByProjectId(...)+owner filter`

4. Security owner check compatibility
- File: `src/main/java/com/codescope/backend/security/util/ProjectSecurity.java`
- Ownership checks now support both `id` and `projectId`.

## Test Updates
- `src/test/java/com/codescope/backend/controller/UploadControllerTest.java`
  - Replaced Firebase mock with `ProjectRepository` mock.
- `src/test/java/com/codescope/backend/service/JobServiceTest.java`
  - Added fallback repository stub for `findById(...)`.
- `src/test/java/com/codescope/backend/service/ProjectServiceTest.java`
  - Added lenient fallback stub for `findByProjectId(...)`.

## Validation
- Command: `mvn -q test`
- Result: pass

## Remaining for Phase 6
- Decommission `FirebaseService` and legacy `JobRunner` paths still tied to Firestore.
- Finalize one canonical project identifier model and backfill existing documents.
