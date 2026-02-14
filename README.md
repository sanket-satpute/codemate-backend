# 🧠 AI Code Analyzer Backend

Welcome to the **AI Code Analyzer Backend**, a modular, multi-model AI inference system built with **Spring Boot (Java)**.

This backend enables seamless integration with multiple LLM providers:
- 🦙 **Ollama** (Local inference)
- 🤗 **Hugging Face**
- 🔮 **OpenAI**

It supports **asynchronous job processing**, **auto-fallback between providers**, and **standardized response schemas**.

---

## 🚀 Current System Overview

| Component | Status | Description |
|------------|---------|-------------|
| **Ollama Integration** | ✅ Done | Successfully running local inference. |
| **Hugging Face Integration** | ✅ Done | Model endpoint fixed and fallback enabled. |
| **OpenAI Integration** | ✅ Done | Integrated and configurable via `application.yml`. |
| **Job Manager** | ✅ Done | Asynchronous execution using Job IDs. |
| **Router & Fallback** | ✅ Done | Automatically switches model if failure occurs. |
| **Config Management** | ✅ Done | AI provider switching via `application.yml`. |
| **Testing Suite** | ✅ Done | Comprehensive JUnit and WebFlux tests for services and controllers. |
| **Reactive Stack** | ✅ Done | Fully reactive, non-blocking implementation. |
| **Centralized Error Handling** | ✅ Done | Global exception handling with `@ControllerAdvice`. |
| **File Validation** | ✅ Done | Robust validation for uploaded files and zip contents. |
| **Security (Auth & WebSocket)** | ✅ Done | JWT-secured REST and WebSocket endpoints. |

---

## 🏗️ System Architecture

```

User → /api/analyze → ModelRouter → AIService → Provider (Ollama / HF / OpenAI)
↓
Job Queue
↓
Result → JSON { jobId, status, result }

````

**Flow Summary:**
1. User sends a POST request to `/api/analyze` with JSON:
   ```json
   { "code": "your code snippet here" }
````

2. Backend assigns a unique `jobId`.
3. Request goes through the **ModelRouter**, which selects the best AI model (Ollama / HF / OpenAI) based on configuration.
4. If primary model fails → **Fallback** to local Ollama or another configured model.
5. Response is standardized:

   ```json
   {
     "jobId": "uuid",
     "status": "COMPLETED",
     "result": "AI analysis text"
   }
   ```

---

## ⚙️ Current Tech Stack

| Layer            | Technology                          |
| ---------------- | ----------------------------------- |
| Language         | Java 21                             |
| Framework        | Spring Boot 3.x (WebFlux)           |
| HTTP Client      | WebClient (Reactive)                |
| Async Processing | Reactor (Mono/Flux)                 |
| Local Model      | Ollama (via REST API)               |
| Remote Models    | Hugging Face / OpenAI               |
| Build Tool       | Maven                               |
| Tests            | JUnit 5 / Mockito / WebTestClient   |
| Database         | Firebase Firestore                  |
| File Storage     | Cloudinary                          |
| Authentication   | JWT + Firebase Auth                 |
| WebSocket        | Spring WebFlux WebSocket            |

---

## 📡 API Endpoints

| Method | Endpoint                  | Description                               | Security |
| ------ | ------------------------- | ----------------------------------------- | -------- |
| `POST` | `/api/auth/login`         | Authenticate user with Firebase token     | Public   |
| `POST` | `/api/upload/project`     | Create a new project                      | Secured  |
| `POST` | `/api/upload/file`        | Upload file(s) for analysis               | Secured  |
| `POST` | `/api/analyze`            | Submit code snippet for AI analysis       | Secured  |
| `POST` | `/api/analyze/files`      | Submit uploaded files for AI analysis     | Secured  |
| `POST` | `/api/correct`            | Request AI code correction                | Secured  |
| `GET`  | `/api/job/{jobId}`        | Check AI analysis job status              | Secured  |
| `GET`  | `/api/result/{jobId}`     | Fetch AI analysis result                  | Secured  |
| `POST` | `/api/reports`            | Create (dummy) report                     | Secured  |
| `GET`  | `/api/reports/project/{projectId}` | Get all reports for a project      | Secured  |
| `GET`  | `/api/reports/{reportId}/download` | Download report as PDF             | Secured  |
| `GET`  | `/api/export/{jobId}`     | Get report for export (by job ID)         | Secured  |
| `GET`  | `/api/projects/{id}`      | Get single project details                | Secured  |
| `GET`  | `/api/projects`           | List all projects (with jobs & reports)   | Secured  |
| `DELETE`| `/api/projects/{id}`     | Delete project                            | Secured  |
| `WS`   | `/ws/chat`                | Real-time AI chat for code review         | Secured  |

---

## 🧩 Folder Structure

```
src/
 └── main/
     ├── java/com/codescope/backend/
     │   ├── ai/
     │   │   ├── AIServiceFactory.java
     │   │   ├── HuggingFaceService.java
     │   │   ├── OllamaService.java
     │   │   └── OpenAIService.java
     │   ├── config/
     │   │   ├── CorsConfig.java
     │   │   ├── FirebaseConfig.java
     │   │   ├── GlobalExceptionHandler.java
     │   │   ├── SecurityConfig.java
     │   │   ├── SwaggerConfig.java
     │   │   └── WebSocketConfig.java
     │   ├── controller/
     │   │   ├── AnalysisController.java
     │   │   ├── AuthController.java
     │   │   ├── ChatController.java
     │   │   ├── ExportController.java
     │   │   ├── ProjectController.java
     │   │   ├── ReportController.java
     │   │   └── UploadController.java
     │   ├── dto/
     │   │   ├── AnalysisRequestDTO.java
     │   │   ├── CorrectionRequestDTO.java
     │   │   ├── FileUploadRequestDTO.java
     │   │   └── JobStatusResponseDTO.java
     │   ├── exception/
     │   │   ├── InvalidInputException.java
     │   │   ├── ResourceNotFoundException.java
     │   │   └── UnauthorizedException.java
     │   ├── model/
     │   │   ├── AIRequest.java
     │   │   ├── AIResponse.java
     │   │   ├── AnalysisJob.java
     │   │   ├── ChatMessage.java
     │   │   ├── FileDocument.java
     │   │   ├── Project.java
     │   │   ├── Report.java
     │   │   └── User.java
     │   ├── repository/
     │   │   ├── AIResponseRepository.java
     │   │   └── AnalysisJobRepository.java
     │   ├── security/
     │   │   ├── JwtAuthenticationFilter.java
     │   │   └── JwtUtil.java
     │   └── service/
     │       ├── AIService.java
     │       ├── AnalysisService.java
     │       ├── ChatService.java
     │       ├── FileStorageService.java
     │       ├── FirebaseService.java
     │       ├── JobRunner.java
     │       ├── JobService.java
     │       ├── ProjectService.java
     │       └── ReportService.java
     └── resources/
         ├── application.yml
         ├── firebase-service-account.json
         ├── ai/
         │   └── ai_response_schema_v1.json
         └── static/
         └── templates/
```

---

## 🧠 What’s Done

* ✅ Fully reactive backend with Spring WebFlux.
* ✅ Implemented real JWT + Firebase Auth (replacing mock login).
* ✅ Secured WebSocket endpoint with reactive security.
* ✅ Removed all blocking calls (`.block()`) in reactive streams.
* ✅ Centralized error handling with `@ControllerAdvice`.
* ✅ Replaced dummy reports with actual AI analysis results.
* ✅ Validated uploaded files, zip contents, and code snippets.
* ✅ Wrote comprehensive unit & integration tests (JUnit + Mockito + WebTestClient).
* ✅ Fixed Hugging Face deprecated endpoint and enabled fallback.
* ✅ Enabled configuration-based AI provider switching (`application.yml`).
* ✅ Optimized async job execution, Firebase queries, and file storage.
* ✅ Secured all API endpoints with `@PreAuthorize("isAuthenticated()")`.

---

## 🔧 What’s In Progress

*   None. All priority tasks are completed.

---

## 🚀 What’s Next

*   📊 Create AI analytics dashboard.
*   ☁️ Dockerize backend and setup CI/CD pipeline.
*   📈 Implement rate limiting for API endpoints.

---

## 🧪 Example Request / Response

### Request

```bash
POST http://localhost:8080/api/analyze
Content-Type: application/json
Authorization: Bearer YOUR_JWT_TOKEN

{
  "projectId": "your-project-id",
  "code": "public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello, World!\"); } }"
}
```

### Response

```json
{
  "jobId": "ab1234cd-5678-ef90-1234-gh5678ijkl90",
  "status": "PENDING",
  "message": "Job started successfully"
}
```

---

## 💡 Developer Notes

*   Local Ollama must be running (`ollama serve`) if configured as the default AI provider.
*   Ensure `HF_API_KEY` and `OPENAI_API_KEY` environment variables are set for Hugging Face and OpenAI integration, respectively.
*   Configure `ai.defaultModel` in `application.yml` to switch between `huggingface`, `openai`, or `ollama`.

---

## 👨‍💻 Author & Team

**Project Owner:** Sanket Satpute
**Backend Team:** AI Inference & Routing Engineers
**Tech Stack:** Java • Spring Boot • Ollama • Hugging Face • OpenAI • Firebase • Cloudinary • JWT
**Repo:** `GITHUB | PORTFOLIO`

---

## 🧾 License

This project is under the MIT License — free to use, modify, and distribute.
