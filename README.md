# Android Antigravity

A Phase 1 foundation for a phone-first AI coding companion. Android Antigravity is a small, functioning vertical slice connecting a Jetpack Compose chat UI to a FastAPI backend over Server-Sent Events (SSE), with local conversation history and zero model API keys stored on the device.

---

## 📁 Repository Structure

```text
AntiGravity/
└── AndroidAntigravity/
    ├── app/                     # Jetpack Compose Android client (Kotlin, Material 3, Ktor, DataStore)
    │   ├── build.gradle.kts     # App build configuration (minSdk 26, targetSdk 36, compileSdk 37)
    │   └── src/main/java/com/androidantigravity/
    │       ├── MainActivity.kt  # Root activity hosting Jetpack Compose UI
    │       ├── core/            # Auth, model, network (Ktor SSE client), and DataStore storage
    │       ├── feature/chat/    # ChatScreen Compose view & ChatViewModel logic
    │       └── ui/theme/        # Material 3 theme definitions
    ├── backend/                 # FastAPI server (Python 3.12+, Uvicorn, Pydantic)
    │   ├── app/main.py          # FastAPI routes, SSE streaming handler, and AssistantProvider adapters
    │   ├── requirements.txt     # Python dependencies (fastapi, uvicorn, google-genai, google-auth)
    │   ├── .env.example         # Environment template for model & auth configuration
    │   └── tests/               # Backend API tests (pytest, httpx)
    ├── build.gradle.kts         # Root Gradle build script
    ├── settings.gradle.kts      # Gradle settings & plugin repositories
    ├── local.properties.example # Local properties template for Google Web Client ID
    └── README.md                # Component-level documentation
```

---

## 🛠️ Requirements & Setup

### Prerequisites
- **Python**: 3.12 or newer
- **Android Studio**: Configured to use **JDK 17+**
- **Android Device / Emulator**: Running Android 8.0 (API Level 26) or newer

---

### 1. Run the Backend API

From the project root:

```powershell
cd AndroidAntigravity/backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Verify backend health at `http://127.0.0.1:8000/health`. By default, the server runs with `AI_PROVIDER=demo`, streaming synthetic responses without needing model credentials.

---

### 2. Configure Gemini AI Provider (Optional)

To stream using Google Gemini:

1. Copy `AndroidAntigravity/backend/.env.example` to `AndroidAntigravity/backend/.env`:
   ```powershell
   cp AndroidAntigravity/backend/.env.example AndroidAntigravity/backend/.env
   ```
2. Edit `backend/.env`:
   ```env
   AI_PROVIDER=gemini
   GEMINI_API_KEY=your_gemini_api_key
   GEMINI_MODEL=gemini-3.6-flash
   ```
3. Restart the uvicorn server.

---

### 3. Run the Android App

1. Open the `AndroidAntigravity` directory in Android Studio.
2. Ensure JDK 17+ is selected in **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK**.
3. Run the backend server first.
4. Launch an emulator or connect a physical Android device.

#### Network Configuration
- **Android Emulator**: Uses `http://10.0.2.2:8000` to access host localhost.
- **Physical Device**: Update `API_BASE_URL` in `app/build.gradle.kts` to your computer's LAN IP address (e.g. `http://192.168.1.14:8000`) and ensure both host and phone are on the same local network.

---

## 🔐 Google Sign-In Setup

1. In Google Cloud Console, configure the OAuth consent screen and create:
   - An **Android OAuth Client** for package `com.androidantigravity` using your SHA-1 fingerprint.
   - A **Web application OAuth Client**.
2. Copy the **Web application client ID** into:
   - `AndroidAntigravity/local.properties`: `GOOGLE_WEB_CLIENT_ID=...`
   - `AndroidAntigravity/backend/.env`: `GOOGLE_WEB_CLIENT_ID=...`
3. Restart the backend and rebuild the Android app. Tap **Sign in** in the app bar to authenticate via Google Credential Manager; the backend verifies the ID token at `/v1/auth/google`.

---

## 📡 API Endpoints

| Endpoint | Method | Input Payload | Output / Response |
| :--- | :---: | :--- | :--- |
| `/health` | `GET` | None | `{"status": "ok", "provider": "demo"}` |
| `/v1/chat/stream` | `POST` | `{"messages": [{"role": "user", "content": "..."}]}` | Server-Sent Events (`data: {"type": "delta", "text": "..."}`) |
| `/v1/auth/google` | `POST` | `{"id_token": "..."}` | `{"subject": "...", "email": "...", "name": "...", "picture": "..."}` |

---

## 🗺️ Roadmap & Phase Boundaries

| Phase | Description | Status |
| :--- | :--- | :---: |
| **Phase 1** | Auth boundary, Compose Chat UI, SSE streaming, DataStore history, Markdown renderer | ✅ **Implemented** |
| **Phase 2** | SAF project import, file tree navigator, code editor, Room project index | ⏳ Planned |
| **Phase 3** | Scoped file tools, diff previews, change approval UI, agent task logs | ⏳ Planned |
| **Phase 4** | GitHub OAuth, repo clone/commit/branching & PR reviews | ⏳ Planned |
| **Phase 5** | Durable agent background runs, work queues, tests, MCP/plugin permissions | ⏳ Planned |
