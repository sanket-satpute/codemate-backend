# 📂 Upload & Firestore Team Tasklist — Phase 2: Code Ingestion Layer

## 📘 Project Context
This module manages **code ingestion and storage** for the CodeScope platform.  
It allows users to upload raw text, multiple files, or ZIP archives, then stores everything in Firestore for analysis by the AI layer.

Goal: Build a clean, modular upload-to-store pipeline that feeds AIService and QueryService.

---

## ✅ CURRENT STATUS
- ✅ Controller + base structure available.
- ⚙️ Firestore integration partially set up (needs config & schema finalization).
- ⚠️ Upload endpoint skeleton only — file extraction & validation pending.

---

## 🎯 SPRINT OBJECTIVE
Deliver a **fully functional Upload → Firestore pipeline**:
1. Accept text, files, or ZIP uploads.
2. Normalize and store content.
3. Return a `projectId` for further AI analysis.

---

## 🧩 MODULE STRUCTURE

**Path:**  
`/backend/src/main/java/com/codescope/upload/`

upload/
├── UploadController.java
├── UploadService.java
├── FileParser.java
├── ZipUtils.java
├── model/
│ ├── Project.java
│ ├── FileMetadata.java
│ └── UploadResponse.java
├── config/
│ └── FirestoreConfig.java
└── docs/
└── upload_team_tasklist.md

yaml
Copy code

---

## ⚙️ TASKS TO IMPLEMENT NOW (Sprint 2)

### 1️⃣ **Implement UploadController.java**
**Endpoints:**
```java
POST /api/projects/upload
Accepts:

JSON body (for text code)

Multipart (for file[] or ZIP)

Return:

json
Copy code
{ "projectId": "uuid", "status": "STORED" }
Example Implementation:

java
Copy code
@PostMapping("/upload")
public ResponseEntity<?> uploadProject(
        @RequestPart(required = false) String text,
        @RequestPart(required = false) MultipartFile[] files,
        @RequestPart(required = false) MultipartFile zip) {
    return ResponseEntity.ok(uploadService.handleUpload(text, files, zip));
}
2️⃣ Implement UploadService.java
Core logic:

Validate incoming data (reject empty upload).

If ZIP → call ZipUtils.extractFiles().

If files[] → iterate & read content.

If text → treat as single file (main.txt).

Create Firestore project document:

json
Copy code
{
  "projectId": "uuid",
  "files": [{ "filename": "X.java", "language": "Java", "content": "..." }],
  "createdAt": "timestamp"
}
Save via firebaseService.saveProject().

3️⃣ Implement ZipUtils.java
Responsibilities:

Extract .zip into a temp folder.

List all contained files with relative paths.

Filter out non-code files (like .png, .exe).

Return a List<FileMetadata>.

Sample logic:

java
Copy code
public static List<FileMetadata> extractFiles(MultipartFile zip) {
    // unzip → collect → map(filename, content, language)
}
Use Java’s ZipInputStream to handle decompression.

4️⃣ Implement FileParser.java
Responsibilities:

Identify programming language by file extension.

Optionally detect main file for project summary.

Generate FileMetadata objects:

java
Copy code
new FileMetadata(filename, content, language);
Example mapping:

css
Copy code
.java → Java
.py → Python
.js → JavaScript
.html → HTML
.css → CSS
5️⃣ FirestoreConfig.java
Path: /upload/config/FirestoreConfig.java

Configure Firestore connection via Firebase Admin SDK:

java
Copy code
@Bean
public Firestore getFirestore() throws IOException {
    GoogleCredentials credentials = GoogleCredentials
        .fromStream(new ClassPathResource("firebase-key.json").getInputStream());
    FirestoreOptions options = FirestoreOptions.newBuilder()
        .setCredentials(credentials)
        .build();
    return options.getService();
}
Add firebase-key.json to /resources/ (not committed to git).

6️⃣ Data Models
📄 Project.java
java
Copy code
@Data
public class Project {
    private String projectId;
    private List<FileMetadata> files;
    private Timestamp createdAt;
}
📄 FileMetadata.java
java
Copy code
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileMetadata {
    private String filename;
    private String language;
    private String content;
}
📄 UploadResponse.java
java
Copy code
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponse {
    private String projectId;
    private String status;
}
7️⃣ Update FirebaseService.java
Add:

java
Copy code
public String saveProject(Project project) {
    CollectionReference projects = firestore.collection("projects");
    ApiFuture<DocumentReference> ref = projects.add(project);
    return ref.get().getId();
}
🧾 TEST CASES
#	Scenario	Expected Behavior
✅ 1	Upload text only	Saves as main.txt project
✅ 2	Upload multiple files	Saves all with language detection
✅ 3	Upload ZIP	Extracts files & stores cleanly
⚠️ 4	Non-code files in ZIP	Skipped silently
💥 5	Empty upload	Returns HTTP 400

🔮 FUTURE TASKS (Next Sprint)
 Store large files in Cloud Storage, not Firestore.

 Add checksum deduplication (avoid re-uploading same project).

 Support .tar.gz and .rar formats.

 Implement file diff generator for code updates.

👨‍💻 Notes & Recommendations
Always sanitize filenames before writing/extracting.

Limit upload size to 50MB per project.

Use UUID for all project IDs.

Log all upload events with projectId for traceability.

🧭 Maintained By: Upload & Firestore Team
📅 Last Updated: November 2025
🧑‍💻 Manager: Sanket Satpute