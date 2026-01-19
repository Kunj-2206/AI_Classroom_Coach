# AI Classroom Coach (Live Voice)

A runnable Android demo for a **real-time, live-voice Classroom Coach** using Gemini Live.
This project focuses on immediate, classroom-ready guidance for teachers during live class moments.

## Project Goals
- **Android app only** with **live voice streaming** (no recordings, no async chat).
- **Gemini Live** streaming for bidirectional audio.
- AI acts as a **Classroom Coach**, not a tutor.
- **Mandatory response format** enforced in code.

## Architecture Overview
**Modules (implemented or stubbed):**
1. **Android App**
   - Push-to-talk or always-on mode
   - Microphone capture + speaker playback
   - Barge-in (teacher can interrupt AI)
2. **Voice Pipeline**
   - 16kHz PCM input capture
   - 24kHz PCM output playback
3. **AI Logic Layer**
   - System prompt injection
   - One-question clarification gate (prompt instruction)
4. **Response Structure Enforcer**
   - Forces AI output into the required sections
5. **Optional Backend**
   - Not required for this demo (client-only)

## Required System Prompt (Embedded)
The exact system prompt is embedded in `PromptProvider.SYSTEM_PROMPT` and injected on connection.

## How to Run
1. **Open in Android Studio**.
2. Add your Gemini API key to `~/.gradle/gradle.properties`:
   ```
   GEMINI_API_KEY=YOUR_KEY_HERE
   ```
3. **Sync Gradle**, then **Run** the `app` configuration on a device.

> **Permissions:** The app requests microphone access on launch.

## Demo Flow
1. Launch the app.
2. Hold **Push to Talk** and speak a classroom issue (or enable Always-on mode).
3. The app streams 16kHz PCM audio to Gemini Live and receives 24kHz PCM output.
4. Gemini Live responds using the **mandatory response format**, spoken back to the teacher.

**Example teacher prompt:**
> “Class is noisy during fractions lesson. Need quick steps to calm and explain equivalent fractions.”

**Example AI response structure:**
- A) 60-Second Stabilize
- B) Teach the Concept (2 minutes)
- C) Check for Understanding (30 seconds)
- D) Extend for Fast Finishers
- E) If It Still Fails

## Key Files
- `MainActivity.kt` – UI + push-to-talk flow
- `ClassroomCoachViewModel.kt` – MVVM state + streaming coordination
- `GeminiLiveClient.kt` – WebSocket streaming client
- `AudioStreamManager.kt` – audio capture/playback pipeline
- `ResponseStructureEnforcer.kt` – required output format enforcement
- `PromptProvider.kt` – mandatory system prompt injection

## Known Limitations
- Gemini Live payload format may change; adjust `GeminiLiveMessageParser` if needed.
- This demo uses a direct WebSocket connection and minimal error handling.
- Always-on mode is a demo toggle and may be battery intensive.

## Safety & Pedagogy Constraints
- English only
- Calm, short, actionable steps
- No punishment, no jargon, no policy commentary
- Minimal classroom resources assumed (chalkboard + notebooks)

## License
MIT (demo only)
