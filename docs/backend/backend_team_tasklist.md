# 🚀 Backend Team Tasklist — AI Integration Phase

## 🧠 Project Context
This backend is responsible for managing all AI model integrations (Ollama, Hugging Face, OpenAI) under a unified service.  
Goal: Provide a stable `/api/analyze` endpoint that dynamically routes to available AI models.

---

## ✅ CURRENT STATUS (As of Now)
- ✅ Ollama integration — Working successfully (local model calls).
- ⚠️ Hugging Face API — Returns 410 Gone (deprecated inference endpoint).
- 💤 OpenAI integration — Not implemented yet.
- 🎯 Unified API design — `/api/analyze` ready to accept code snippets.

---

## 🧩 TASKS TO IMPLEMENT NOW (Sprint 2 — Model Integration)

### 1️⃣ AIService.java
**Path:** `src/main/java/com/yourapp/service/AIService.java`

**Responsibilities:**
- Define:
  ```java
  public String analyzeWithOllama(String code);
  public String analyzeWithHuggingFace(String code);
  public String analyzeWithOpenAI(String code);
````

* Normalize all responses into:

  ```json
  {
    "model": "ollama",
    "result": "...",
    "status": "SUCCESS"
  }
  ```
* Implement error handling:

    * Try → if 410 Gone from HuggingFace → fallback to Ollama.
    * Catch → log error with `logger.error("AIService Failure: {}", e.getMessage());`

---

### 2️⃣ ModelRouter.java

**Path:** `src/main/java/com/yourapp/service/ModelRouter.java`

**Responsibilities:**

* Read from configuration in `application.yml`:

  ```yaml
  ai:
    defaultModel: ollama
    huggingface:
      enabled: true
    openai:
      enabled: false
  ```
* Routing logic:

  ```java
  if (useOllama) return aiService.analyzeWithOllama(code);
  else if (useHF) return aiService.analyzeWithHuggingFace(code);
  else return aiService.analyzeWithOpenAI(code);
  ```

---

### 3️⃣ Update `application.yml`

**Path:** `src/main/resources/application.yml`
Add:

```yaml
ai:
  defaultModel: ollama
  huggingface:
    enabled: true
    token: YOUR_HF_TOKEN
  openai:
    enabled: false
    token: YOUR_OPENAI_KEY
```

---

### 4️⃣ Controller Validation

**File:** `AIController.java`
**Endpoint:** `POST /api/analyze`

**Expected Behavior:**

* Accepts JSON:

  ```json
  {
    "code": "public class HelloWorld {}"
  }
  ```
* Returns:

  ```json
  {
    "jobId": "uuid",
    "model": "ollama",
    "result": "Analysis result...",
    "status": "SUCCESS"
  }
  ```

---

## 🔍 TEST CASES

| Case | Input                  | Expected Output               |
| ---- | ---------------------- | ----------------------------- |
| ✅ 1  | Valid code snippet     | Returns Ollama result JSON    |
| ⚠️ 2 | HuggingFace model call | 410 Gone → fallback to Ollama |
| 💤 3 | OpenAI disabled        | Returns `"Not Configured"`    |
| 💥 4 | Empty code             | Returns 400 Bad Request       |

---

## 🔮 FUTURE TASKS (Upcoming Sprints)

* [ ] Add async job queue (Redis or Kafka)
* [ ] Save results in DB for history/logging
* [ ] Add “model performance metrics”
* [ ] Enable OpenAI and tune for streaming responses
* [ ] Add authentication middleware for API security

---

### 👷 Team Note

Before pushing:

* Run `mvn clean package`
* Test `/api/analyze` endpoint
* Log all model outputs for comparison

---

🧭 **Maintained By:** Backend Core Team
📅 **Last Updated:** November 2025
🧑‍💻 **Manager:** Sanket Satpute
