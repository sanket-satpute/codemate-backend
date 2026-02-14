# 🧠 AI Integration Team Tasklist — Multi-Model Service Layer

## 📘 Project Context
This module powers the **intelligence layer** of CodeScope.  
It connects to different AI providers — **Ollama (local)**, **OpenAI (cloud)**, and **Hugging Face (fallback)** — via a unified interface.

The goal: Make AIService model-agnostic, fault-tolerant, and easily extendable for new models.

---

## ✅ CURRENT STATUS
- ✅ `AIService` & `ModelRouter` structure created.
- ✅ Ollama integration **successful** (`/api/analyze` works).
- ⚠️ Hugging Face & OpenAI connectors pending.
- ⚙️ Error handling partially functional (410 Gone → fallback required).

---

## 🎯 SPRINT OBJECTIVE
Deliver a **multi-model AIService** that:
1. Routes requests based on selected model.
2. Handles response normalization.
3. Stores all responses with job metadata.

---

## 🧩 MODULE STRUCTURE

**Path:**  
`/backend/src/main/java/com/codescope/ai/`

ai/
├── AIService.java
├── ModelRouter.java
├── clients/
│ ├── OllamaClient.java
│ ├── OpenAIClient.java
│ └── HuggingFaceClient.java
├── models/
│ ├── AIRequest.java
│ └── AIResponse.java
├── utils/
│ └── AIResponseParser.java
└── docs/
└── ai_team_tasklist.md

yaml
Copy code

---

## ⚙️ TASKS TO IMPLEMENT NOW (Sprint 2)

### 1️⃣ **Expand ModelRouter.java**
**Goal:** Central routing logic for all models.

```java
public AIResponse route(String model, String code) {
    switch(model.toLowerCase()) {
        case "openai":
            return openAIClient.analyze(code);
        case "huggingface":
            return huggingFaceClient.analyze(code);
        default:
            return ollamaClient.analyze(code);
    }
}
Add fallback:
If OpenAI or HF fail → auto-route to Ollama.

Add error logs & timestamps.

2️⃣ Implement OpenAIClient.java
Path: /ai/clients/OpenAIClient.java

Responsibilities:

Use WebClient or OkHttpClient.

POST to: https://api.openai.com/v1/chat/completions

Headers:

pgsql
Copy code
Authorization: Bearer ${OPENAI_API_KEY}
Content-Type: application/json
Sample payload:

json
Copy code
{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "system", "content": "You are a code analyzer." },
    { "role": "user", "content": "Analyze this code..." }
  ]
}
Expected Output:

json
Copy code
{
  "id": "cmpl-xyz",
  "choices": [{ "message": { "content": "This code defines..." } }]
}
Normalize into:

java
Copy code
return new AIResponse("openai", jobId, content, "SUCCESS");
3️⃣ Implement HuggingFaceClient.java
Path: /ai/clients/HuggingFaceClient.java

Responsibilities:

POST to:
https://api-inference.huggingface.co/models/codellama/CodeLlama-7b-Instruct-hf

Header: Authorization: Bearer ${HF_API_KEY}

Handle 410 Gone (model deprecated → fallback to Ollama).

Response Normalization Example:

java
Copy code
return new AIResponse("huggingface", jobId, parsedResult, "SUCCESS");
4️⃣ Enhance OllamaClient.java
Ensure ollamaClient.analyze() supports both raw text and code file context.

Endpoint: http://localhost:11434/api/generate

Use async WebClient call with streaming enabled.

Parse result JSON and return unified AIResponse.

5️⃣ Integrate AIResponseParser.java
Path: /ai/utils/AIResponseParser.java

Responsibility:

Normalize all model outputs into a common JSON format:

json
Copy code
{
  "model": "string",
  "jobId": "uuid",
  "result": "string",
  "status": "SUCCESS/FAILED",
  "timestamp": "ISO-8601"
}
Sanitize incomplete or malformed model outputs.

6️⃣ Add Firestore Logging
For every AI response, call:

java
Copy code
firestoreService.saveAIResult(projectId, aiResponse);
Ensures historical traceability for user queries.

🧾 TEST CASES
#	Scenario	Expected Behavior
✅ 1	Analyze with Ollama	Returns valid summary
✅ 2	OpenAI key valid	Returns gpt-4o-mini response
⚠️ 3	HF model 410 Gone	Fallback to Ollama
💥 4	Missing API key	Returns error JSON with status FAILED
🔁 5	Multi-call sequence	Stores each result in Firestore

🔮 FUTURE TASKS (Next Sprint)
 Implement async job queue for large code uploads.

 Add model benchmarking (compare responses).

 Stream OpenAI responses live to frontend.

 Introduce prompt templates system (configurable per model).

👨‍💻 Notes & Recommendations
Store all secrets in application.properties:

ini
Copy code
openai.api.key=sk-...
huggingface.api.key=hf_...
Use dependency injection for all clients (Spring @Component).

Keep logs structured with Slf4j for observability.

All responses should be validated before returning to controller.

🧭 Maintained By: AI Integration Team
📅 Last Updated: November 2025
🧑‍💻 Manager: Sanket Satpute