# Android Antigravity

A Phase 1 foundation for a phone-first AI coding companion. It is intentionally a small, functioning vertical slice: Compose chat UI → SSE API → assistant provider, with local conversation history.

## What is included

- Jetpack Compose, Kotlin, Material 3 chat UI with assistant/user messages.
- Incremental server-sent response rendering.
- A small Markdown renderer with fenced code blocks and bold text.
- Local chat-history persistence through DataStore.
- FastAPI API with request validation, CORS, an SSE stream and a deterministic demo provider.
- Clear boundaries for future authentication, model, filesystem, Git, and agent features.

## Run the backend

Use Python 3.12 or later:

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Verify it at `http://127.0.0.1:8000/health`. The default `demo` provider streams a response without any model credential.

## Run the Android app

1. Install Android Studio and configure it to use JDK 17 or newer. This machine's Java 8 cannot build current Android Gradle Plugin releases.
2. Open this `AndroidAntigravity` folder in Android Studio and allow Gradle sync.
3. Start the backend first.
4. Run an emulator. It uses `http://10.0.2.2:8000` by default, which maps the emulator to the host machine.

For a physical phone, replace `API_BASE_URL` in `app/build.gradle.kts` with your computer's LAN address, for example `http://192.168.1.20:8000`, and ensure both devices are on the same private network. Use HTTPS before any production deployment; `usesCleartextTraffic` is enabled only to make local development work.

## Production model and Google Sign-In

The app deliberately has **no model SDK or API key**: those credentials must remain server-side. To use Gemini, copy `backend/.env.example` to `backend/.env`, then set `AI_PROVIDER=gemini` and `GEMINI_API_KEY`. Restart the backend after updating dependencies with `pip install -r requirements.txt`. `backend/.env` is ignored by Git. Never ship a Gemini key in the APK.

Google Sign-In is the next Phase 1 wiring step because it needs your own Android OAuth client, server OAuth client ID, and server-side ID-token verification. Use Android Credential Manager / Google ID for the client flow, post the ID token to a new authenticated backend endpoint, verify its audience against the server client ID, and return a short-lived session token. Do not trust an ID token purely on the device.

## Google Sign-In setup

1. In Google Cloud Console, configure the consent screen and create an Android OAuth client for package `com.androidantigravity` using your app-signing SHA-1, plus a **Web application** OAuth client.
2. Add the Web application client ID to both `local.properties` (`GOOGLE_WEB_CLIENT_ID=...`) and `backend/.env` (`GOOGLE_WEB_CLIENT_ID=...`). Do not commit either file.
3. Restart FastAPI and rebuild the Android app. The app bar’s **Sign in** button opens Credential Manager; the backend validates the ID token signature and audience before returning the profile.

## Phase boundaries

| Phase | Additions |
| --- | --- |
| 1 (this foundation) | Auth boundary, chat, SSE, persistence, Markdown/code rendering |
| 2 | SAF project import, file tree, editor, search, Room project index |
| 3 | Scoped file tools, diffs, approval UI, agent task log |
| 4 | GitHub OAuth, clone/commit/branches/PR review |
| 5 | Durable agent runs, work queues, tests, MCP/plugin permissions |

The important constraint is that file and Git operations in later phases stay server-scoped to an explicit project root, require user approval for mutations, and return diffs before they write.
