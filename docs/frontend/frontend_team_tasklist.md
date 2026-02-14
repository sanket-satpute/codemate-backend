# 🎨 Frontend Team Tasklist — AI Integration Phase

## 🌐 Project Context
This frontend (Angular 17) is the user-facing part of **CodeScope**, interacting with backend `/api/analyze` for code analysis using multiple AI models (Ollama, Hugging Face, OpenAI).

Goal: Provide an intuitive dashboard where users can upload, edit, and analyze their code via AI and visualize the results clearly.

---

## ✅ CURRENT STATUS (As of Now)
- ✅ Angular workspace set up
- ✅ Routing and module structure ready
- ⚙️ API layer pending integration
- 🎯 UI mockups ready from UI/UX team (Login → Dashboard → Upload → Analyze → Results)

---

## 🧩 TASKS TO IMPLEMENT NOW (Sprint 2 — Backend Connectivity)

### 1️⃣ **Create API Service**
**Path:** `src/app/services/api.service.ts`

**Responsibilities:**
- Use `HttpClient` to connect to backend.
- Add method:
  ```typescript
  analyzeCode(code: string): Observable<any> {
    return this.http.post('http://localhost:8080/api/analyze', { code });
  }
Handle success & failure responses using RxJS catchError.

Expected response:

json
Copy code
{
"jobId": "uuid",
"model": "ollama",
"result": "Code analysis summary...",
"status": "SUCCESS"
}
2️⃣ Build Upload & Analysis UI
Path: src/app/components/analyzer/analyzer.component.ts

Responsibilities:

Provide a text editor (use Monaco or Ace editor).

Add an Analyze button that triggers:

typescript
Copy code
this.apiService.analyzeCode(this.userCode)
.subscribe(res => this.result = res.result);
Show spinner while waiting.

Display AI model name, jobId, and result in a card view.

3️⃣ Integrate Model Selection Dropdown
Path: Same component (analyzer.component.ts)

UI Example:

css
Copy code
[ Ollama ▼ ]
[ HuggingFace ▼ ]
[ OpenAI ▼ ]
Functionality:

Send selected model as query param:

typescript
Copy code
this.http.post(`/api/analyze?model=${this.selectedModel}`, { code });
4️⃣ Add Analysis Result Visualization
Path: src/app/components/results/results.component.ts

Display formatted output with syntax highlighting.

Add “Copy Result” and “Export as JSON” buttons.

Future enhancement: Add chart comparing results between models.

5️⃣ Implement Global State Management
Library: NgRx or simple BehaviorSubject

Store: current jobId, selected model, result text.

6️⃣ Error Handling & UX Enhancements
Case	Action
Backend Down	Show “Backend Offline” alert
410 Gone	Display “Model Deprecated — switched to Ollama”
Empty Input	Disable “Analyze” button
Success	Smooth fade-in of results

🔍 TEST CASES
#	Action	Expected Result
✅ 1	Analyze with Ollama	Shows valid analysis
⚠️ 2	Select HuggingFace	Auto fallback if fails
💥 3	No internet	Shows “Offline” banner
💤 4	Multiple analyses	Displays latest one only

🔮 FUTURE TASKS (Upcoming Sprints)
Add user auth (Firebase Auth) for personalized dashboard

Enable model comparison chart

Add dark/light theme switcher

Enable real-time stream from backend (Server-Sent Events)

Integrate history tab to view past analysis jobs

👷 Team Notes
Before pushing:

Run ng lint && ng test

Ensure .env or environment.ts points to correct backend URL

Verify CORS enabled on backend (Spring Boot side)

🧭 Maintained By: Frontend Team
📅 Last Updated: November 2025
🧑‍💻 Manager: Sanket Satpute