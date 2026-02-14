# CodeScope Frontend API Reference

This guide provides Angular frontend developers with a clear reference to all available backend APIs, including REST endpoints and WebSocket channels.

---

## 1. Authentication

### Endpoint
`POST /api/auth/login`

**Purpose:**  
Authenticates a user by verifying their Firebase ID token and returns a session JWT.

**Authentication:**  
Public (no JWT required).

**Request Body Example:**
```json
{
  "token": "firebase_id_token_from_frontend"
}
```

**Response Body Example (Success):**
```json
{
  "token": "your_jwt_token",
  "email": "user@example.com"
}
```

**Angular Service Integration:**
```typescript
// In your AuthService
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = '/api/auth';

  constructor(private http: HttpClient) {}

  login(firebaseToken: string) {
    return this.http.post<{ token: string; email: string }>(`${this.apiUrl}/login`, { token: firebaseToken });
  }
}
```
**Usage Tip:** After a successful Firebase login on the client-side, send the resulting ID token to this endpoint. Store the returned JWT (e.g., in `localStorage`) and attach it to subsequent requests using an `HttpInterceptor`.

---

## 2. Analysis API

### Endpoint
`POST /api/analyze`

**Purpose:**  
Triggers an AI code analysis job for a raw string of code.

**Authentication:**  
Requires JWT.

**Request Body Example:**
```json
{
  "projectId": "your_project_id",
  "code": "public class HelloWorld { ... }"
}
```

**Response Body Example (Success):**
```json
{
  "jobId": "your_job_id",
  "status": "PENDING",
  "message": "Job started successfully"
}
```

**Angular Service Integration:**
```typescript
// In your AnalysisService
analyzeCode(projectId: string, code: string) {
  return this.http.post('/api/analyze', { projectId, code });
}
```

### Endpoint
`POST /api/analyze/files`

**Purpose:**  
Triggers an AI code analysis job by uploading one or more files.

**Authentication:**  
Requires JWT.

**Request Body Example:**
This endpoint expects `multipart/form-data`.

**Response Body Example (Success):**
```json
{
  "jobId": "your_job_id",
  "status": "PENDING",
  "message": "Job started successfully with uploaded files",
  "uploadedFiles": ["url_to_file1", "url_to_file2"]
}
```

**Angular Service Integration:**
```typescript
// In your AnalysisService
analyzeFiles(projectId: string, files: File[]) {
  const formData = new FormData();
  formData.append('projectId', projectId);
  files.forEach(file => formData.append('files', file, file.name));
  
  return this.http.post('/api/analyze/files', formData);
}
```

### Endpoint
`POST /api/correct`

**Purpose:**  
Corrects a piece of code based on a natural language instruction.

**Authentication:**  
Requires JWT.

**Request Body Example:**
```json
{
  "originalContent": "public void func() { ... }",
  "instruction": "Refactor this method to be more efficient.",
  "projectId": "optional_project_id",
  "fileName": "optional_file_name.java"
}
```

**Response Body Example (Success):**
```json
{
  "message": "Code corrected and stored successfully.",
  "correctedFileUrl": "url_to_corrected_file",
  "correctedFileName": "corrected_file_name",
  "aiModelUsed": "AI_MODEL_UNKNOWN",
  "timestamp": "2023-10-27T10:00:00Z"
}
```

**Angular Service Integration:**
```typescript
// In your AnalysisService
correctCode(payload: { originalContent: string; instruction: string; }) {
  return this.http.post('/api/correct', payload);
}
```

### Endpoint
`GET /api/job/{jobId}`

**Purpose:**  
Fetches the current status of a specific analysis job.

**Authentication:**  
Requires JWT.

**Response Body Example (Success):**
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

**Angular Service Integration:**
```typescript
// In your JobService
getJobStatus(jobId: string) {
  return this.http.get(`/api/job/${jobId}`);
}
```
**Usage Tip:** Poll this endpoint periodically after starting an analysis job to update the UI on its progress.

### Endpoint
`GET /api/result/{jobId}`

**Purpose:**  
Fetches the detailed result of a completed analysis job.

**Authentication:**  
Requires JWT.

**Response Body Example (Success):**
The response is the AI-generated report, which can be in various formats (JSON, text, etc.).
```json
{
  "summary": "Analysis complete.",
  "details": "...",
  "findings": [
    { "line": 10, "issue": "Potential null pointer exception." }
  ]
}
```

**Angular Service Integration:**
```typescript
// In your JobService
getJobResult(jobId: string) {
  return this.http.get(`/api/result/${jobId}`);
}
```

---

## 3. Project Management API

### Endpoint
`POST /api/projects`

**Purpose:**  
Creates a new project for a user.

**Authentication:**  
Requires JWT.

**Request Body Example:**
```json
{
  "name": "My New Project",
  "description": "A project for code analysis.",
  "userId": "firebase_user_id"
}
```

**Response Body Example (Success):**
```json
{
  "projectId": "new_project_id",
  "status": "CREATED"
}
```

**Angular Service Integration:**
```typescript
// In your ProjectService
createProject(projectData: { name: string; description: string; userId: string; }) {
  return this.http.post('/api/projects', projectData);
}
```

### Endpoint
`GET /api/projects`

**Purpose:**  
Lists all projects for the currently authenticated user.

**Authentication:**  
Requires JWT.

**Response Body Example (Success):**
```json
{
  "count": 1,
  "projects": [
    {
      "projectId": "project_id_xyz",
      "name": "My Project",
      "description": "...",
      "userId": "firebase_user_id",
      "createdAt": "2023-10-27T10:00:00Z",
      "jobs": []
    }
  ]
}
```

**Angular Service Integration:**
```typescript
// In your ProjectService
getProjects() {
  return this.http.get('/api/projects');
}
```

### Endpoint
`GET /api/projects/{id}`

**Purpose:**  
Retrieves a single project by its ID, including its associated jobs and reports.

**Authentication:**  
Requires JWT.

**Response Body Example (Success):**
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
      "report": { "reportId": "report_id_123", "content": "..." }
    }
  ]
}
```

**Angular Service Integration:**
```typescript
// In your ProjectService
getProjectById(id: string) {
  return this.http.get(`/api/projects/${id}`);
}
```

### Endpoint
`DELETE /api/projects/{id}`

**Purpose:**  
Deletes a project by its ID.

**Authentication:**  
Requires JWT.

**Response Body Example (Success):**
```json
{
  "projectId": "deleted_project_id",
  "status": "DELETED"
}
```

**Angular Service Integration:**
```typescript
// In your ProjectService
deleteProject(id: string) {
  return this.http.delete(`/api/projects/${id}`);
}
```

---

## 4. File Upload API

### Endpoint
`POST /api/upload/project`

**Purpose:**  
Uploads a new project with multiple files included in the payload.

**Authentication:**  
Requires JWT.

**Request Body Example:**
```json
{
  "name": "New Project with Files",
  "userId": "firebase_user_id",
  "files": [
    { "fileName": "main.py", "content": "print('hello')" }
  ]
}
```

**Response Body Example (Success):**
```json
{
  "projectId": "new_project_id",
  "jobId": "initial_analysis_job_id",
  "status": "PENDING"
}
```

### Endpoint
`POST /api/upload/file`

**Purpose:**  
Uploads a single file for analysis, creating a new project implicitly.

**Authentication:**  
Requires JWT.

**Request Body Example:**
This endpoint expects `multipart/form-data`.

**Response Body Example (Success):**
```json
{
  "projectId": "generated_project_id",
  "jobId": "analysis_job_id",
  "fileCount": 1,
  "status": "PENDING",
  "fileUrls": ["url_to_uploaded_file"]
}
```

**Angular Service Integration:**
```typescript
// In your UploadService
uploadFile(file: File) {
  const formData = new FormData();
  formData.append('file', file, file.name);
  
  return this.http.post('/api/upload/file', formData);
}
```

---

## 5. Reports and Dashboard API

### Endpoint
`GET /api/dashboard`

**Purpose:**  
Fetches aggregated dashboard data (project counts, job stats) for the user.

**Authentication:**  
Requires JWT.

**Request Query Parameter:**
- `userId` (string, required)

**Response Body Example (Success):**
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

**Angular Service Integration:**
```typescript
// In your DashboardService
getDashboardData(userId: string) {
  return this.http.get(`/api/dashboard?userId=${userId}`);
}
```

### Endpoint
`GET /api/reports/project/{projectId}`

**Purpose:**  
Retrieves all reports associated with a specific project.

**Authentication:**  
Requires JWT.

**Response Body Example (Success):**
```json
[
  {
    "reportId": "report_id_1",
    "projectId": "project_id_abc",
    "jobId": "job_id_123",
    "status": "COMPLETED",
    "summary": "...",
    "details": [],
    "findings": []
  }
]
```

**Angular Service Integration:**
```typescript
// In your ReportService
getReportsForProject(projectId: string) {
  return this.http.get(`/api/reports/project/${projectId}`);
}
```

### Endpoint
`GET /api/reports/{reportId}/download`

**Purpose:**  
Downloads a specific report as a PDF file.

**Authentication:**  
Requires JWT.

**Response:**  
A PDF file (`application/pdf`).

**Angular Service Integration:**
```typescript
// In your ReportService
downloadReport(reportId: string) {
  return this.http.get(`/api/reports/${reportId}/download`, {
    responseType: 'blob' // Important for handling file downloads
  });
}
```
**Usage Tip:** The response is a `Blob`. You can create a URL from it (`URL.createObjectURL`) and trigger a download programmatically in the browser.

---

## 6. Chat API (REST & WebSocket)

### Endpoint (REST)
`GET /api/chat/{projectId}/{fileId}`

**Purpose:**  
Fetches the chat history for a specific file within a project.

**Authentication:**  
Requires JWT.

**Response Body Example (Success):**
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

**Angular Service Integration:**
```typescript
// In your ChatService
getChatHistory(projectId: string, fileId: string) {
  return this.http.get(`/api/chat/${projectId}/${fileId}`);
}
```

### Endpoint (WebSocket)
`WS /ws/chat`

**Purpose:**  
Provides a real-time, bidirectional communication channel for chat.

**Authentication:**  
The initial WebSocket handshake is public, but messages should be handled securely.

**Destinations:**
- **Client-to-Server:** `/app/chat/{projectId}/{fileId}`
- **Server-to-Client:** `/topic/messages/{projectId}/{fileId}`

**Message Payload Example (`ChatMessage`):**
```json
{
  "message": "Can you explain this function?",
  "sender": "user",
  "timestamp": "2023-10-27T10:05:00Z"
}
```

**Angular Service Integration:**
Use a library like `@stomp/stompjs` and `sockjs-client` to manage the STOMP connection over WebSocket.

```typescript
// In your WebSocketService
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// ... setup client
const client = new Client({
  webSocketFactory: () => new SockJS('/ws/chat'),
  // ... other config
});

// Subscribe to a topic
client.subscribe(`/topic/messages/${projectId}/${fileId}`, message => {
  const chatMessage = JSON.parse(message.body);
  // Handle incoming message
});

// Send a message
client.publish({
  destination: `/app/chat/${projectId}/${fileId}`,
  body: JSON.stringify(chatMessage),
});
