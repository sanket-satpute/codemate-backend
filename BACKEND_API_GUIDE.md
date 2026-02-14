# CodeScope Backend API Guide

This document provides a comprehensive guide to the CodeScope backend system. It is intended for frontend developers to understand the available API endpoints, data structures, and overall architecture.

## 1. API List

This section details all the available API endpoints.

### Authentication

#### POST /api/auth/login

*   **Purpose:** Authenticates a user with a Firebase ID token and returns a JWT.
*   **Input:**
    *   **Payload:** JSON
    *   **Fields:**
        *   `token` (string, required): The Firebase ID token obtained from the frontend.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "token": "your_jwt_token",
          "email": "user@example.com"
        }
        ```
    *   **Error (400 Bad Request):** If the `token` is missing.
    *   **Error (401 Unauthorized):** If the Firebase token is invalid.
*   **Authentication:** None required.

### Analysis

#### POST /api/analyze

*   **Purpose:** Triggers an AI code analysis job with a raw code string.
*   **Input:**
    *   **Payload:** JSON
    *   **Fields:**
        *   `projectId` (string, required): The ID of the project this analysis belongs to.
        *   `code` (string, required): The code to be analyzed.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "jobId": "your_job_id",
          "status": "PENDING",
          "message": "Job started successfully"
        }
        ```
*   **Authentication:** JWT required.

#### POST /api/analyze/files

*   **Purpose:** Triggers an AI code analysis job with one or more uploaded files.
*   **Input:**
    *   **Payload:** `multipart/form-data`
    *   **Fields:**
        *   `projectId` (string, required): The ID of the project.
        *   `files` (file[], required): The file(s) to be analyzed.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "jobId": "your_job_id",
          "status": "PENDING",
          "message": "Job started successfully with uploaded files",
          "uploadedFiles": ["url_to_file1", "url_to_file2"]
        }
        ```
*   **Authentication:** JWT required.

#### POST /api/correct

*   **Purpose:** Corrects a piece of code based on a given instruction.
*   **Input:**
    *   **Payload:** JSON
    *   **Fields:**
        *   `originalContent` (string, required): The code to be corrected.
        *   `instruction` (string, required): The instruction for the AI.
        *   `projectId` (string, optional): The project to associate the corrected file with.
        *   `fileName` (string, optional): The original name of the file.
        *   `fileType` (string, optional): The type of the file.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "message": "Code corrected and stored successfully.",
          "correctedFileUrl": "url_to_corrected_file",
          "correctedFileName": "corrected_file_name",
          "aiModelUsed": "AI_MODEL_UNKNOWN",
          "timestamp": "2023-10-27T10:00:00Z"
        }
        ```
*   **Authentication:** JWT required.

#### GET /api/job/{jobId}

*   **Purpose:** Fetches the status of an analysis job.
*   **Input:**
    *   **Path Parameter:** `jobId` (string, required).
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "jobId": "your_job_id",
          "projectId": "your_project_id",
          "status": "COMPLETED",
          "resultId": "your_result_id",
          "createdAt": "2023-10-27T10:00:00Z",
          "completedAt": "2023-10-27T10:05:00Z"
        }
        ```
*   **Authentication:** JWT required.

#### GET /api/result/{jobId}

*   **Purpose:** Fetches the result of a completed analysis job.
*   **Input:**
    *   **Path Parameter:** `jobId` (string, required).
*   **Output:**
    *   **Success (200 OK):** The AI-generated report (structure may vary).
*   **Authentication:** JWT required.

### Chat

#### GET /api/chat/{projectId}/{fileId}

*   **Purpose:** Fetches the chat history for a specific file within a project.
*   **Input:**
    *   **Path Parameters:**
        *   `projectId` (string, required)
        *   `fileId` (string, required)
*   **Output:**
    *   **Success (200 OK):** A list of `ChatMessage` objects.
        ```json
        [
          {
            "messageId": "unique_id",
            "projectId": "your_project_id",
            "fileId": "your_file_id",
            "sender": "user",
            "message": "Hello, AI!",
            "timestamp": "2023-10-27T10:00:00Z"
          }
        ]
        ```
*   **Authentication:** JWT required.

#### WebSocket /app/chat/{projectId}/{fileId}

*   **Purpose:** Sends a chat message over WebSocket.
*   **Input:**
    *   **Destination Variables:** `projectId`, `fileId`
    *   **Payload:** `ChatMessage` object.
*   **Output:** The message is broadcast to `/topic/messages/{projectId}/{fileId}`.
*   **Authentication:** See Section 2.

### Dashboard

#### GET /api/dashboard

*   **Purpose:** Fetches aggregated dashboard data for a user.
*   **Input:**
    *   **Query Parameter:** `userId` (string, required).
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "totalProjects": 10,
          "totalJobs": 50,
          "successfulJobs": 45,
          "failedJobs": 5,
          "modelUsage": {
            "huggingface": 20,
            "openai": 30
          }
        }
        ```
*   **Authentication:** JWT required.

### Export

#### GET /api/export/{jobId}

*   **Purpose:** Fetches a report associated with a specific job ID.
*   **Input:**
    *   **Path Parameter:** `jobId` (string, required).
*   **Output:**
    *   **Success (200 OK):** A `Report` object (structure may vary based on report content).
        ```json
        {
          "reportId": "report_id_123",
          "jobId": "job_id_abc",
          "projectId": "project_id_xyz",
          "generatedAt": "2023-10-27T10:00:00Z",
          "content": "Detailed AI analysis report content in markdown/text format."
        }
        ```
    *   **Error (404 Not Found):** If the report for the given `jobId` is not found.
*   **Authentication:** JWT required.

### Projects

#### POST /api/projects

*   **Purpose:** Creates a new project.
*   **Input:**
    *   **Payload:** JSON
    *   **Fields:** A `Project` object (e.g., `name`, `description`, `userId`).
        ```json
        {
          "name": "My New Project",
          "description": "A project for code analysis.",
          "userId": "firebase_user_id"
        }
        ```
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "projectId": "new_project_id",
          "status": "CREATED"
        }
        ```
*   **Authentication:** JWT required.

#### GET /api/projects/{id}

*   **Purpose:** Retrieves details for a single project, including associated jobs and reports.
*   **Input:**
    *   **Path Parameter:** `id` (string, required): The ID of the project.
*   **Output:**
    *   **Success (200 OK):** A `Project` object with nested `AnalysisJob` and `Report` data.
        ```json
        {
          "projectId": "project_id_xyz",
          "name": "My Project",
          "description": "...",
          "userId": "firebase_user_id",
          "createdAt": "2023-10-27T10:00:00Z",
          "jobs": [
            {
              "jobId": "job_id_abc",
              "status": "COMPLETED",
              "report": {
                "reportId": "report_id_123",
                "content": "..."
              }
            }
          ]
        }
        ```
    *   **Error (404 Not Found):** If the project is not found.
*   **Authentication:** JWT required.

#### GET /api/projects

*   **Purpose:** Lists all projects for the authenticated user, including associated jobs and reports.
*   **Input:** None.
*   **Output:**
    *   **Success (200 OK):** A map containing the count and a list of `Project` objects.
        ```json
        {
          "count": 2,
          "projects": [
            {
              "projectId": "project_id_xyz",
              "name": "My Project",
              "description": "...",
              "userId": "firebase_user_id",
              "createdAt": "2023-10-27T10:00:00Z",
              "jobs": [...]
            }
          ]
        }
        ```
*   **Authentication:** JWT required.

#### DELETE /api/projects/{id}

*   **Purpose:** Deletes a project by its ID.
*   **Input:**
    *   **Path Parameter:** `id` (string, required): The ID of the project to delete.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "projectId": "deleted_project_id",
          "status": "DELETED"
        }
        ```
*   **Authentication:** JWT required.

### Reports

#### POST /api/reports

*   **Purpose:** Creates a dummy report (primarily for testing PDF generation and storage). Can optionally accept a `Report` object in the request body.
*   **Input:**
    *   **Payload:** JSON (optional)
    *   **Fields:** A `Report` object (e.g., `reportId`, `projectId`, `jobId`, `status`, `summary`, `details`, `findings`). If not provided, a default dummy report is created.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "reportId": "generated_report_id",
          "message": "Report saved successfully!"
        }
        ```
    *   **Error (500 Internal Server Error):** If an error occurs during report saving.
*   **Authentication:** JWT required.

#### GET /api/reports/project/{projectId}

*   **Purpose:** Retrieves all reports associated with a specific project.
*   **Input:**
    *   **Path Parameter:** `projectId` (string, required).
*   **Output:**
    *   **Success (200 OK):** A list of `Report` objects.
        ```json
        [
          {
            "reportId": "report_id_1",
            "projectId": "project_id_abc",
            "jobId": "job_id_123",
            "status": "COMPLETED",
            "summary": "...",
            "details": [...],
            "findings": [...]
          }
        ]
        ```
    *   **Error (500 Internal Server Error):** Returns an empty list on error.
*   **Authentication:** JWT required.

#### GET /api/reports/{reportId}/download

*   **Purpose:** Downloads a specific report as a PDF file.
*   **Input:**
    *   **Path Parameter:** `reportId` (string, required).
*   **Output:**
    *   **Success (200 OK):** A PDF file (`application/pdf`) as a byte array.
    *   **Error (404 Not Found):** If the report is not found.
    *   **Error (500 Internal Server Error):** If an error occurs during PDF generation.
*   **Authentication:** JWT required.

### Upload

#### POST /api/upload/project

*   **Purpose:** Uploads a new project, which can include multiple files.
*   **Input:**
    *   **Payload:** JSON
    *   **Fields:** A `Project` object.
        *   `name` (string, required): The name of the project.
        *   `description` (string, optional): A description of the project.
        *   `userId` (string, required): The ID of the user creating the project.
        *   `files` (FileDocument[], optional): A list of `FileDocument` objects associated with the project.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "projectId": "new_project_id",
          "jobId": "initial_analysis_job_id",
          "status": "PENDING"
        }
        ```
    *   **Error (400 Bad Request):** If the project name is missing.
*   **Authentication:** JWT required.

#### POST /api/upload/file

*   **Purpose:** Uploads a single file for analysis. This endpoint handles file validation, storage, and initiates AI analysis.
*   **Input:**
    *   **Payload:** `multipart/form-data`
    *   **Fields:**
        *   `file` (file, required): The file to upload.
*   **Constraints:**
    *   Max file size: 10 MB
    *   Allowed extensions: `java`, `py`, `js`, `ts`, `html`, `css`, `json`, `xml`, `yml`, `yaml`, `md`, `txt`, `zip`, `pdf`, `docx`.
*   **Output:**
    *   **Success (200 OK):**
        ```json
        {
          "projectId": "generated_project_id",
          "jobId": "analysis_job_id",
          "fileCount": 1,
          "status": "PENDING",
          "fileUrls": ["url_to_uploaded_file"]
        }
        ```
    *   **Error (400 Bad Request):** If no file is uploaded, file size exceeds limit, or file type is unsupported.
*   **Authentication:** JWT required.

---

## 2. Authentication & Security

The CodeScope backend uses a combination of Firebase Authentication and JWT (JSON Web Tokens) for securing its API endpoints.

### Authentication Flow

1.  **Frontend obtains Firebase ID Token:** The frontend application is responsible for authenticating users with Firebase (e.g., Google Sign-In, email/password). Upon successful authentication, Firebase provides an ID token.
2.  **Frontend sends Firebase ID Token to Backend:** The frontend sends this Firebase ID token to the backend's `/api/auth/login` endpoint.
3.  **Backend verifies Firebase ID Token:** The backend uses the Firebase Admin SDK to verify the authenticity and validity of the received Firebase ID token.
4.  **Backend generates JWT:** If the Firebase ID token is valid, the backend generates its own JWT. This JWT contains claims such as the user's email and roles (currently, all authenticated users have the "USER" role).
5.  **Backend returns JWT to Frontend:** The generated JWT is returned to the frontend.
6.  **Frontend uses JWT for subsequent requests:** For all subsequent authenticated API requests, the frontend must include this JWT in the `Authorization` header as a Bearer token (e.g., `Authorization: Bearer <your_jwt_token>`).

### Endpoint Security

*   **`/api/auth/login`**: This endpoint **does not require authentication** as it is the entry point for obtaining a JWT.
*   **All other `/api/**` endpoints**: All other REST API endpoints under `/api/` require a valid JWT in the `Authorization` header. This is enforced by the `JwtAuthenticationFilter` and `@PreAuthorize("isAuthenticated()")` annotations on controller methods.
*   **WebSocket Endpoints (`/ws/chat/**`)**: These endpoints are configured to allow unauthenticated access for the initial WebSocket handshake. However, individual messages or operations within the WebSocket session might still require authentication or authorization checks at the application level, depending on the `ChatService` implementation. For this application, the `SecurityConfig` explicitly permits `/ws/chat/**` paths.

### Permissions / Roles

Currently, the system uses a simple role-based authorization:
*   All successfully authenticated users are assigned the `USER` role.
*   The `@PreAuthorize("isAuthenticated()")` annotation ensures that only authenticated users (those with a valid JWT) can access the protected resources. More granular role-based authorization can be implemented by checking specific roles (e.g., `@PreAuthorize("hasRole('ADMIN')")`).

---

---

## 3. Data Flow & Storage

The CodeScope backend employs a reactive, non-blocking architecture using Spring WebFlux. Data flows through various services, primarily interacting with Firebase for persistent storage and Redis for caching. Cloudinary is used for external file storage.

### General Data Flow

1.  **Request Ingestion:** API requests are received by Spring WebFlux controllers.
2.  **Service Layer Processing:** Controllers delegate business logic to various services (e.g., `ProjectService`, `JobService`, `AnalysisService`, `ChatService`, `FileStorageService`, `DashboardService`, `ReportService`).
3.  **Data Interaction:** Services interact with Firebase for primary data persistence and Redis for caching. File content is processed and stored in Cloudinary.
4.  **Reactive Streams:** All operations are handled reactively using `Mono` and `Flux` from Project Reactor, ensuring non-blocking execution.

### Storage Mechanisms

*   **Firebase (Firestore):**
    *   **Projects:** `Project` objects are stored in Firestore, serving as the primary database for project metadata.
    *   **Analysis Jobs:** `AnalysisJob` objects, which track the status and results of AI analysis tasks, are persistently stored in Firestore.
    *   **AI Responses:** `AIResponse` objects, containing the detailed output from AI models, are stored in Firestore.
    *   **Chat Messages:** `ChatMessage` objects, representing user and AI interactions, are stored in Firestore for chat history.
    *   **Reports:** `Report` objects, generated from AI analysis results, are stored in Firestore.
    *   **Users:** User metadata (e.g., email) is managed through Firebase Authentication and potentially stored in Firestore if additional user profiles are needed.

*   **Redis Cache:**
    *   **Job Tracking:** `AnalysisJob` objects are cached in Redis. When a job is started or its status is updated, it's stored in Redis with a Time-To-Live (TTL) of 24 hours.
    *   **Fallback Mechanism:** If a job is not found in the Redis cache (cache miss), the system falls back to fetching the job details from Firebase. Upon successful retrieval from Firebase, the job is then cached in Redis for future fast access.
    *   **Performance Optimization:** Redis significantly speeds up the retrieval of job statuses and results, especially under high concurrency, reducing the load on Firebase.

*   **Cloudinary:**
    *   **File Storage:** All uploaded files (code files, documents, zip contents) are stored externally on Cloudinary. The `FileDocument` model stores the Cloudinary URL, allowing the backend to retrieve files as needed. This offloads large binary data storage from Firebase and provides a robust media management solution.

---

---

## 4. AI Analysis Flow

The AI analysis flow is designed to be reactive, scalable, and resilient, supporting multiple AI providers with a fallback mechanism.

### Request Handling

1.  **Initiation:** AI analysis can be initiated via two main API endpoints:
    *   `POST /api/analyze`: For raw code text submission.
    *   `POST /api/analyze/files`: For file uploads (single, multiple, or zip).
2.  **Job Creation:** Upon receiving an analysis request, the `JobService` creates a new `AnalysisJob` with a unique `jobId` and marks its status as `PENDING`. This job is immediately persisted to Firebase and cached in Redis.
3.  **Asynchronous Execution:** The actual AI analysis is executed asynchronously by the `JobRunner` service. This ensures that the API request returns quickly, providing the `jobId` to the frontend, while the heavy AI processing happens in the background.

### AI Models Used

The backend supports multiple AI providers, configured via `application.yml`:

*   **OllamaService:** Integrates with a local Ollama instance (e.g., `http://localhost:11434`) for models like `llama2`.
*   **HuggingFaceService:** Connects to Hugging Face Inference API, using a configurable default model (e.g., `google/flan-t5-base`). Requires `HF_API_KEY`.
*   **OpenAIService:** Integrates with OpenAI's API. Requires `OPENAI_API_KEY`.

The `AIServiceFactory` is responsible for providing the correct `AIService` implementation based on the `ai.defaultModel` configuration.

### Fallback Mechanism

The system is designed with a fallback mechanism for AI providers:

1.  If the primary AI service (defined by `ai.defaultModel`) fails or is unavailable, the system can be configured to attempt analysis with a secondary provider.
2.  The `AIService` interface and its implementations (`OllamaService`, `HuggingFaceService`, `OpenAIService`) abstract the AI interaction, making it easier to switch or add new providers.
3.  In case of an AI service failure during job execution, the `JobRunner` catches the error, marks the `AnalysisJob` as `FAILED`, and updates both Firebase and Redis.

### Job Queue Management and Result Fetching

1.  **Job Status Tracking:** The frontend can poll the `GET /api/job/{jobId}` endpoint to retrieve the current status of an `AnalysisJob` (e.g., `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`).
2.  **Redis Caching:** `JobService` first attempts to fetch job status from Redis. If a cache miss occurs, it retrieves the job from Firebase and then caches it in Redis for subsequent requests. This ensures fast retrieval of job statuses.
3.  **Result Retrieval:** Once a job's status is `COMPLETED`, the frontend can fetch the detailed AI report using the `GET /api/result/{jobId}` endpoint. This endpoint retrieves the associated `Report` from Firebase.

---

---

## 5. File Handling

The backend provides robust file handling capabilities, supporting various file types and sizes for AI analysis.

### Upload Types Supported

*   **Single File Upload:** Individual code files or documents can be uploaded directly via `POST /api/upload/file`.
*   **Multiple File Uploads:** The `POST /api/analyze/files` endpoint allows for uploading multiple files simultaneously.
*   **Zip File Uploads:** The system can process `.zip` archives. Upon upload, the zip file is extracted, and its contents are individually processed and stored. This is handled transparently by the `FileStorageService`.

### Validation Rules

The `UploadController` enforces the following validation rules for file uploads:

*   **Maximum File Size:** Individual files must not exceed **10 MB**.
*   **Allowed File Extensions:** Only the following file types are supported for analysis:
    *   Code: `.java`, `.py`, `.js`, `.ts`, `.html`, `.css`
    *   Data/Config: `.json`, `.xml`, `.yml`, `.yaml`
    *   Text/Markdown: `.md`, `.txt`
    *   Archives: `.zip`
    *   Documents: `.pdf`, `.docx`
*   **Empty Files:** Empty files are ignored during processing.

### Storage and Retrieval of Uploaded Files

1.  **Processing:** The `FileStorageService` is responsible for processing uploaded `MultipartFile` objects. This includes:
    *   Extracting text content from supported document types (PDF, DOCX).
    *   Decompressing `.zip` files and processing each entry.
    *   Storing the raw content of code/text files.
2.  **Cloudinary Storage:** All processed files (or their extracted content) are uploaded to Cloudinary, a cloud-based media management service. Cloudinary provides a secure and scalable solution for storing large files.
3.  **Firebase Metadata:** For each file stored in Cloudinary, a `FileDocument` object containing metadata (including the Cloudinary URL) is created and stored in Firebase. This allows the backend to link files to projects and retrieve their URLs when needed.
4.  **Content Extraction:** For AI analysis, the textual content of uploaded files is extracted and concatenated into a single string, separated by `--- FILE SEPARATOR ---` markers. This combined text is then sent to the AI service.

---

---

## 6. Chat / WebSocket Flow

The CodeScope backend provides real-time chat functionality between users and AI models, primarily facilitated by WebSockets.

### WebSocket Configuration

*   **Endpoint:** The WebSocket endpoint is `/ws/chat`. Frontend clients should connect to this endpoint.
*   **STOMP Protocol:** The backend uses the STOMP (Simple Text Oriented Messaging Protocol) over WebSockets. Frontend clients should use a STOMP client library to interact with this endpoint.
*   **Message Broker:** A simple in-memory message broker is configured.
    *   **Application Destination Prefix:** `/app` - Messages sent from clients to the server should be prefixed with `/app` (e.g., `/app/chat/{projectId}/{fileId}`).
    *   **User Destination Prefix:** `/topic` - Messages broadcast from the server to clients are sent to topics prefixed with `/topic` (e.g., `/topic/messages/{projectId}/{fileId}`).

### How Chat Messages are Sent/Received

1.  **Client Sends Message:** A frontend client sends a `ChatMessage` object to the WebSocket endpoint `/app/chat/{projectId}/{fileId}`. The `ChatMessage` object is expected to contain:
    *   `message` (string): The content of the user's message
    *   `projectId` (string): The ID of the project.
    *   `fileId` (string): The ID of the file the chat is associated with.
    *   `sender` (string): "user" for messages from the client.
    *   `timestamp` (string, ISO 8601 format): The timestamp of the message.
2.  **Server Processes Message:** The `ChatController` receives the message, sets the `sender` to "user", and saves the message to Firebase.
3.  **Server Broadcasts User Message:** The user's message is immediately broadcast back to the `/topic/messages/{projectId}/{fileId}` topic, allowing all subscribed clients (including the sender) to display it in real-time.
4.  **AI Response Generation:** The `ChatService` then handles generating an AI response based on the user's message.
5.  **AI Response Streaming:** The AI response is streamed back to the client(s) in real-time, also via the `/topic/messages/{projectId}/{fileId}` topic.

### Message Storage

*   All `ChatMessage` objects (both user and AI messages) are persistently stored in Firebase (Firestore). This allows for retrieving chat history via the `GET /api/chat/{projectId}/{fileId}` REST endpoint.

### Real-time Flow and Handling

*   The use of WebSockets and STOMP ensures a low-latency, real-time communication channel for chat.
*   The reactive nature of the backend (Spring WebFlux) ensures that message processing and AI response generation/streaming are non-blocking and scalable.
*   The `SimpMessagingTemplate` is used by the server to send messages to specific WebSocket topics.

---

---

## 7. Reports & Dashboard

The backend provides functionalities for generating comprehensive reports from AI analysis results and exposing aggregated metrics for a user dashboard.

### Report Generation

1.  **Trigger:** Reports are generated after an AI analysis job (`AnalysisJob`) is completed. The `ReportService` is responsible for this process.
2.  **Content:** Reports (`Report` objects) encapsulate the summary, detailed findings, and other relevant information from the AI's output.
3.  **Storage:** Generated reports are stored persistently in Firebase (Firestore).
4.  **PDF Download:** Users can download reports as PDF files via the `GET /api/reports/{reportId}/download` endpoint. The `ReportService` handles the dynamic generation of these PDF documents.

### Metrics and Analytics Endpoints for Dashboard

The `DashboardController` exposes an endpoint to provide aggregated metrics for the frontend dashboard:

*   **`GET /api/dashboard`**:
    *   **Purpose:** Fetches key performance indicators and usage statistics for a given user.
    *   **Metrics Provided:**
        *   `totalProjects`: Total number of projects created by the user.
        *   `totalJobs`: Total AI analysis jobs submitted by the user.
        *   `successfulJobs`: Number of successfully completed AI jobs.
        *   `failedJobs`: Number of failed AI jobs.
        *   `modelUsage`: A map showing the count of jobs processed by each AI model (e.g., `{"huggingface": 20, "openai": 30}`).
    *   **Filtering:** Currently, metrics are filtered by `userId`. Further filtering by `projectId` or `AI model` can be implemented in the `DashboardService` if needed.
    *   **Optimization:** Responses are optimized for frontend charts and tables, providing raw counts and aggregated data.

---

---

## 8. Dependencies & Configurations

The CodeScope backend relies on several external dependencies and requires specific configurations, primarily managed through `application.yml` and environment variables.

### Environment Variables / API Keys

The following environment variables are crucial for the backend's operation. These should be set in the deployment environment (e.g., Docker, Kubernetes, CI/CD pipeline) or in a `.env` file for local development.

*   **`JWT_SECRET` (required):** A strong, secret key used for signing and verifying JWTs. **Must be changed in production.**
    *   *Example (development default):* `your-secret-key-for-jwt-development-only-change-in-production`
*   **`HF_API_KEY` (optional, if Hugging Face is used):** API key for accessing the Hugging Face Inference API.
*   **`OPENAI_API_KEY` (optional, if OpenAI is used):** API key for accessing OpenAI's API.
*   **`CLOUDINARY_URL` (required):** The Cloudinary environment variable, typically in the format `cloudinary://<api_key>:<api_secret>@<cloud_name>`. This is used by the `FileStorageService` to connect to Cloudinary for file uploads.

### Application Configuration Files

The backend uses Spring Boot's flexible configuration system, primarily `application.yml`, with profile-specific overrides.

*   **`src/main/resources/application.yml` (Base Configuration):**
    ```yaml
    spring:
      profiles:
        active: "@spring.profiles.active@" # Activated by Maven profile or environment variable
      application:
        name: backend
      main:
        web-application-type: reactive
      codec:
        max-in-memory-size: 10MB # Increased buffer for large file uploads
      data:
        redis:
          host: localhost # Default for local development
          port: 6379      # Default for local development

    server:
      port: 8080

    # Firebase Configuration
    firebase:
      project-id: codescope-ai-code-reviewer
      service-account: firebase-service-account.json # Path to your service account key file (relative to resources folder)

    # Cloudinary Configuration (can be overridden by CLOUDINARY_URL env var)
    cloudinary:
      cloud-name: your_cloud_name
      api-key: your_api_key
      api-secret: your_api_secret

    # AI Service Configuration
    ai:
      defaultModel: huggingface # Can be 'huggingface', 'openai', or 'ollama'
      huggingface:
        token: ${HF_API_KEY} # Environment variable for Hugging Face API Key
        default-model: google/flan-t5-base # Configurable default model for Hugging Face
      openai:
        token: ${OPENAI_API_KEY} # Environment variable for OpenAI API Key
      ollama:
        base-url: http://localhost:11434 # Ollama local instance URL
        default-model: llama2 # Default model for Ollama

    # JWT Configuration
    jwt:
      secret: ${JWT_SECRET:your-secret-key-for-jwt-development-only-change-in-production} # Fallback for development
      expiration: 3600000 # 1 hour in milliseconds

    logging:
      level:
        root: INFO
        com.codescope.backend: DEBUG # Detailed logging for backend packages in dev
        org.springframework.web.reactive.function.client: DEBUG # Log WebClient requests/responses
    ```

*   **`src/main/resources/application-dev.yml` (Development Profile):**
    *   Activated when `spring.profiles.active=dev`.
    *   Overrides base settings for development-specific configurations, such as Redis host/port (if different from `localhost`) and more verbose logging.
    ```yaml
    spring:
      data:
        redis:
          host: localhost
          port: 6379

    logging:
      level:
        root: INFO
        com.codescope.backend: DEBUG
        org.springframework.web.reactive.function.client: DEBUG
    ```

*   **`src/main/resources/application-prod.yml` (Production Profile):**
    *   Activated when `spring.profiles.active=prod`.
    *   Overrides base settings for production-specific configurations, such as Redis host (to point to the Docker service name) and less verbose logging.
    ```yaml
    spring:
      data:
        redis:
          host: redis # Points to the 'redis' service in docker-compose
          port: 6379

    logging:
      level:
        root: INFO
        com.codescope.backend: INFO # Less verbose logging in production
    ```

### Docker Configuration

*   **`Dockerfile`**: Defines the steps to build the backend application into a Docker image. It uses OpenJDK 17, copies Maven dependencies and source, then packages and runs the JAR.
*   **`docker-compose.yml`**: Orchestrates the backend service and a Redis instance for local development or deployment.
    ```yaml
    version: '3.8'
    services:
      backend:
        build: . # Builds from the Dockerfile in the current directory
        ports:
          - "8080:8080" # Maps container port 8080 to host port 8080
        depends_on:
          - redis # Ensures Redis starts before the backend
        environment: # Environment variables passed to the backend container
          - SPRING_DATA_REDIS_HOST=redis # Overrides application.yml to point to the Redis service
          - JWT_SECRET=your-super-secret-key-for-production # IMPORTANT: Set a strong secret!
          - HF_API_KEY=your_hugging_face_api_key
          - OPENAI_API_KEY=your_openai_api_key

      redis:
        image: "redis:alpine" # Uses a lightweight Redis image
        ports:
          - "6379:6379" # Maps container port 6379 to host port 6379
    ```

---

---

## 9. Code / Class Mapping

This section provides a high-level overview of the key classes in the backend and their primary responsibilities, illustrating how controllers, services, and other components interact.

### Controllers

Controllers are responsible for handling incoming HTTP requests, performing basic validation, and delegating business logic to the appropriate services. They also format the responses.

*   **`AuthController`**: Handles user authentication via Firebase ID tokens and JWT generation.
*   **`AnalysisController`**: Manages AI code analysis and correction requests, including raw code and file uploads.
*   **`ChatController`**: Manages chat history retrieval (REST) and real-time chat messaging (WebSocket).
*   **`DashboardController`**: Provides aggregated analytics and metrics for the user dashboard.
*   **`ExportController`**: Handles the retrieval of reports.
*   **`ProjectController`**: Manages CRUD operations for projects (create, get, list, delete).
*   **`ReportController`**: Manages report-related operations, including dummy report creation, fetching project reports, and PDF downloads.
*   **`UploadController`**: Handles file uploads, including validation, storage, and initiation of AI analysis for uploaded files.

### Services

Services encapsulate the core business logic, interacting with repositories, external APIs (Firebase, Cloudinary, AI models), and other services.

*   **`AIService` (Interface)**: Defines the contract for AI interaction.
    *   **`HuggingFaceService`**: Implementation of `AIService` for Hugging Face models.
    *   **`OllamaService`**: Implementation of `AIService` for local Ollama models.
    *   **`OpenAIService`**: Implementation of `AIService` for OpenAI models.
*   **`AIServiceFactory`**: A factory class responsible for providing the correct `AIService` implementation based on configuration.
*   **`AnalysisService`**: Orchestrates the AI analysis process, interacting with `AIService` and `JobService`.
*   **`ChatService`**: Manages chat message storage and AI responses in real-time chat.
*   **`DashboardService`**: Aggregates data from Firebase to generate dashboard metrics.
*   **`FileStorageService`**: Handles file processing (extraction, compression), storage to Cloudinary, and metadata persistence to Firebase.
*   **`FirebaseService`**: Provides an abstraction layer for interacting with Firebase Firestore, handling CRUD operations for various models (Projects, Jobs, Reports, Messages, Files).
*   **`JobRunner`**: Executes AI analysis jobs asynchronously, managing their lifecycle and error handling.
*   **`JobService`**: Manages the creation, status tracking, and result retrieval of `AnalysisJob`s, utilizing Redis for caching and Firebase for persistence.
*   **`ProjectService`**: Manages business logic related to projects, interacting with `FirebaseService`.
*   **`ReportService`**: Handles report creation, retrieval, and PDF generation.

### Security Components

*   **`JwtUtil`**: Utility class for generating and validating JWTs.
*   **`JwtAuthenticationFilter`**: A Spring Security filter that intercepts requests, extracts and validates JWTs, and sets up the security context.
*   **`SecurityConfig`**: Configures Spring Security for WebFlux, defining public and authenticated endpoints, CSRF protection, and CORS settings.

### Configuration Classes

*   **`CorsConfig`**: Configures Cross-Origin Resource Sharing (CORS) for the application.
*   **`FirebaseConfig`**: Initializes the Firebase Admin SDK.
*   **`GlobalExceptionHandler`**: Centralized error handling for consistent API error responses.
*   **`RedisConfig`**: Configures the `ReactiveRedisTemplate` for Redis integration.
*   **`SwaggerConfig`**: Configures Swagger/OpenAPI for API documentation.
*   **`WebSocketConfig`**: Configures WebSocket message brokering using STOMP.

### Models (Data Transfer Objects / Entities)

*   **`AnalysisJob`**: Represents an AI analysis job, tracking its status, associated project, and results.
*   **`AIResponse`**: Stores the detailed output from an AI model.
*   **`ChatMessage`**: Represents a single message in the chat, including sender, content, and timestamp.
*   **`FileDocument`**: Stores metadata about an uploaded file, including its Cloudinary URL and extracted content.
*   **`Project`**: Represents a user's project, containing metadata and a list of associated files.
*   **`Report`**: Stores the generated AI analysis report.
*   **`User`**: (Implicitly handled by Firebase Auth, but could be a model for extended user profiles).
*   **DTOs (e.g., `AnalysisRequestDTO`, `CorrectionRequestDTO`, `DashboardDTO`, `FileUploadRequestDTO`, `JobStatusResponseDTO`)**: Data Transfer Objects used for request and response payloads, ensuring clear API contracts.
