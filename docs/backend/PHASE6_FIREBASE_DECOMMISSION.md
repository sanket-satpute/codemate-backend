# Phase 6: Firebase Decommission + Canonical Project ID Finalization

## Goal
- Remove remaining Firebase/Firestore runtime dependencies.
- Ensure new projects always have a canonical external `projectId`.
- Backfill legacy projects where `projectId` is missing.

## Implemented

1. Removed Firebase runtime components
- Deleted `src/main/java/com/codescope/backend/config/FirebaseConfig.java`
- Deleted `src/main/java/com/codescope/backend/service/FirebaseService.java`
- Deleted `src/main/java/com/codescope/backend/service/JobRunner.java` (legacy Firestore-driven job path)

2. Removed Firebase dependency/config
- `pom.xml`
  - Removed `com.google.firebase:firebase-admin`
  - Removed `spring-cloud-gcp-dependencies` BOM import and related version property
- `src/main/resources/application.yml`
  - Removed `firebase.*` configuration block

3. Canonical project ID for new projects
- `src/main/java/com/codescope/backend/project/service/ProjectService.java`
  - New projects now set `projectId = UUID` during creation.
- `src/main/java/com/codescope/backend/upload/controller/UploadController.java`
  - Upload-created projects now set `projectId = UUID` before first save.

4. Legacy backfill for existing data
- Added `src/main/java/com/codescope/backend/project/service/ProjectIdBackfillRunner.java`
  - Startup task finds projects with blank `projectId` and sets `projectId = id`.
  - Controlled via property:
    - `project.id.backfill.enabled` (default `true`)

5. Firestore-specific model cleanup
- `src/main/java/com/codescope/backend/model/Report.java`
  - Replaced Firestore `@ServerTimestamp` with Spring Data `@CreatedDate`.

## Validation
- Command run: `mvn -q test`
- Result: pass

## Outcome
- Backend runtime no longer depends on Firebase/Firestore services.
- Project identifier contract is stabilized for new records and legacy records are auto-healed.
