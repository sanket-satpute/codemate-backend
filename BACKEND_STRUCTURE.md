# Backend Project Structure and Standards

This document outlines the standardized structure and conventions for the CodeScope backend, built with Spring Boot. Adhering to these guidelines ensures consistency, maintainability, and scalability across all modules.

## 1. Folder Structure

The `src/main/java/com/codescope/backend` directory is organized into the following packages:

*   `config/`: Contains configuration classes for Spring, Firebase, Security, Swagger, etc.
*   `controller/`: Houses REST controllers responsible for handling incoming HTTP requests and returning API responses.
*   `service/`: Contains business logic and orchestrates operations between controllers and repositories.
*   `repository/`: Defines interfaces for data access operations, extending Spring Data interfaces (e.g., `MongoRepository`).
*   `model/`: Contains Plain Old Java Objects (POJOs) representing data entities stored in the database (e.g., MongoDB documents).
*   `dto/`: Data Transfer Objects. Contains classes used for transferring data between layers (e.g., request bodies, response objects).
    *   `dto/auth/`: DTOs related to authentication and user management.
    *   `dto/project/`: DTOs for project creation, retrieval, and updates.
    *   `dto/upload/`: DTOs for file upload and processing.
    *   `dto/job/`: DTOs for managing analysis jobs.
    *   `dto/chat/`: DTOs for chat messages and history.
    *   `dto/analysis/`: DTOs for AI analysis requests and responses.
*   `exception/`: Custom exception classes and global exception handlers.
*   `utils/`: Utility classes for common helper functions (e.g., file manipulation).
*   `security/`: Classes related to security configurations, JWT handling, and authentication filters.
*   `ai/`: Interfaces and implementations for integrating with various AI services.

## 2. Naming Conventions

*   **Classes**: PascalCase (e.g., `ProjectController`, `UserService`).
*   **Interfaces**: PascalCase, often ending with `Service` or `Repository` (e.g., `ProjectService`, `UserRepository`).
*   **Methods**: camelCase (e.g., `getProjectById`, `saveUser`).
*   **Variables**: camelCase (e.g., `projectId`, `userName`).
*   **Constants**: SCREAMING_SNAKE_CASE (e.g., `MAX_FILE_SIZE_BYTES`).
*   **Repositories**: `XyzRepository.java` (e.g., `ProjectRepository.java`).

## 3. Service/Controller Rules

### Services:
*   Annotated with `@Service`.
*   Use constructor-based dependency injection for all dependencies.
*   Contain core business logic.
*   Should not return database entities directly; use DTOs for data transfer.
*   Include basic logging using `@Slf4j` for important operations and error handling.
*   Method names should follow standard Java conventions (e.g., `createProject`, `getProjectById`).

### Controllers:
*   Annotated with `@RestController` and `@RequestMapping("/api/...")`.
*   All endpoints should return a `BaseResponse<T>` for standardized API responses.
*   Use `@Slf4j` for logging incoming requests and responses.
*   Validate method naming to be descriptive and action-oriented.
*   Convert plain `Map` objects in request/response bodies to dedicated DTOs.
*   Use `@PreAuthorize` for securing endpoints.

## 4. DTO Usage Rules

*   DTOs are used to define the structure of data exchanged between the client and server, or between different layers of the backend.
*   They should be simple POJOs with getters, setters, and constructors (Lombok's `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` are encouraged).
*   Avoid including business logic within DTOs.
*   Organize DTOs into subfolders within the `dto/` package based on their domain (e.g., `auth/`, `project/`).

## 5. AI-Analysis Flow Pointers

*   AI service integrations are handled within the `ai/` package.
*   The `AnalysisService` orchestrates calls to AI providers and processes their raw responses into structured `AIResponseDto` objects.
*   Analysis jobs are managed by `JobService` and executed by `JobRunner`.
*   Reports generated from AI analysis are handled by `ReportService`.

## 6. Project Standards

*   **Reactive Programming**: Leverage Spring WebFlux and Reactor for non-blocking, asynchronous operations.
*   **Error Handling**: Use `GlobalExceptionHandler` to provide consistent error responses across the API. Custom exceptions (`CustomException`, `ResourceNotFoundException`, `InvalidInputException`) should be used for specific error scenarios.
*   **Logging**: Utilize SLF4J with Logback for comprehensive and structured logging.
*   **Security**: Implement JWT-based authentication and authorization using Spring Security.
*   **Database**: MongoDB is used for persistent storage.
*   **Cloud Storage**: Cloudinary is used for storing uploaded files.
*   **Firebase**: Firebase is used for user authentication and potentially other services.
