# SOHEIL — Life OS
### v0.2.0 · Jarvis Brain foundation

**SOHEIL** is a local-first personal Life Operating System. **Jarvis** is its intelligence layer.

v0.2 turns the v0.1 planner into the first real cognitive architecture: long-term memory, context selection, persistent Jarvis conversation, confirmation-gated actions, and an optional secure AI backend.

## What works in v0.2

- **Today** — open tasks + current daily state
- **Capture / Inbox** — fast capture of notes, tasks, questions, and ideas
- **Life Areas** — Health, Learning, Work, Relationships, Finance, Personal
- **Daily State** — mood, energy, stress, sleep, short daily note
- **Long-term Memory** — local Room/SQLite memory store
- **Memory Retrieval** — deterministic on-device relevance ranking before AI context is built
- **Context Engine** — sends only selected tasks, state, memories, inbox items and recent conversation
- **Jarvis Conversation** — persistent local conversation history
- **Action Proposals** — AI can propose CREATE_TASK / SAVE_MEMORY / CAPTURE; Android requires explicit approval before any write is executed
- **Local Jarvis fallback** — useful deterministic behavior even with no internet/backend
- **Optional Remote AI** — Android → SOHEIL backend → OpenAI Responses API
- **v1 → v2 Room migration** — existing v0.1 data is preserved
- **Persian-first / RTL UI**

## Architecture

```text
Jetpack Compose UI
        ↓
SoheilViewModel
        ↓
Repository
   ↙         ↘
Room DB      JarvisOrchestrator
                ↓
       Context + Memory selection
           ↙             ↘
   Local Jarvis      Remote Gateway
                          ↓
                  SOHEIL backend
                          ↓
                  OpenAI Responses API
```

The local database remains the source of truth. The model never receives the complete database.

## Security rules

1. **Never put an OpenAI API key in the Android app.** It belongs only on the backend.
2. AI writes are **proposal-only** in v0.2. The user confirms them before execution.
3. `SOHEIL_BACKEND_TOKEN` is only a private-prototype gate; because it is shipped to the client it is not a production secret. Replace it with real user authentication / authorization before public deployment.
4. Release deployment should use HTTPS only.
5. The backend intentionally does not log the user's message or selected life context.

See `docs/SECURITY.md`.

## Build requirements

- Android Studio Quail 2 / compatible
- JDK 17+
- Android SDK 37
- AGP 9.3.0
- Gradle 9.5.0

### Android only — local mode

Open the root folder in Android Studio, sync Gradle, and run the `app` configuration. No backend is required; Jarvis automatically uses Local Mode.

### Enable Remote AI

1. Start the backend in `server/` (see `server/README.md`).
2. Copy `soheil.properties.example` to `soheil.properties`.
3. Set `SOHEIL_BACKEND_URL` and the same prototype backend token.
4. Rebuild the Android app.

For the Android emulator, host-machine localhost is usually `http://10.0.2.2:8787`.

## Product boundary of v0.2

This version intentionally does **not** yet include voice, notifications, Calendar/Health Connect, embeddings/vector search, cloud sync, encryption-at-rest hardening, or autonomous background agents. Those belong to later versions after the memory/action contract is stable.

See `docs/ROADMAP.md` and `docs/JARVIS_BRAIN.md`.
