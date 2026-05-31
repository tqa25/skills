Status: ready-for-agent

# FlowBot — Gesture Automation App (Phase 1 Prototype)

## Problem Statement

The user performs repetitive manual tasks on their Android phone every day — such as navigating to Google News, opening articles one by one, copying each link, and saving it. These workflows are tedious, time-consuming, and error-prone when done by hand. Existing automation tools either require root access, don't support real gesture simulation, or are too complex to configure for personal workflows.

## Solution

An Android app called **FlowBot** that simulates real human gestures (tap, swipe, long press, etc.) on the device using **Shizuku** for system-level input injection — no root required. Users define workflows as JSON files describing a sequence of steps. The app runs these workflows automatically, finding on-screen elements via Android's Accessibility Service (with image matching as a fallback), and saves collected data locally.

Phase 1 is a working prototype scoped to the Google News link collector example: press Home → swipe to Google News → loop through articles → copy link → save locally.

## User Stories

1. As a user, I want to define a workflow as a JSON file with a list of steps, so that I can describe what the app should do without writing code.
2. As a user, I want the app to tap on a specific location on screen, so that it can press buttons for me.
3. As a user, I want the app to long-press on an element, so that it can open context menus like a human would.
4. As a user, I want the app to swipe in any direction, so that it can navigate between screens and scroll through content.
5. As a user, I want the app to pinch-zoom on the screen, so that it can zoom in and out of content.
6. As a user, I want the app to drag and drop elements, so that it can rearrange items on screen.
7. As a user, I want the app to type text (including Vietnamese), so that it can fill in search boxes and forms.
8. As a user, I want the app to press hardware keys (Home, Back, Recent), so that it can navigate the Android system.
9. As a user, I want the app to take screenshots, so that it can use image matching to find elements.
10. As a user, I want the app to read and write the clipboard, so that it can copy links and other text.
11. As a user, I want the app to open a specific app by name, so that workflows can switch between apps.
12. As a user, I want to add a delay between steps, so that the target app has time to load.
13. As a user, I want a step that waits until a specific element appears on screen, so that the workflow doesn't fail on slow-loading content.
14. As a user, I want to loop a set of steps N times, so that I can repeat actions like "copy next article link" without duplicating the workflow definition.
15. As a user, I want to configure error handling per step (retry, skip, or stop), so that the workflow can handle failures gracefully.
16. As a user, I want the app to find on-screen elements by their text or type using Accessibility, so that the workflow adapts to different screen sizes and layouts.
17. As a user, I want the app to fall back to image matching when Accessibility can't find an element, so that it works with apps that have poor accessibility support.
18. As a user, I want to see a log of what each workflow run did (which steps passed/failed, how long each took), so that I can debug problems.
19. As a user, I want to run a workflow from the app UI by pressing a "Run" button, so that I can test it manually.
20. As a user, I want the workflow to keep running when the screen is off, so that I can leave it running unattended.
21. As a user, I want to load, browse, and delete saved workflow JSON files from within the app, so that I can manage my workflows.
22. As a user, I want the collected data (e.g. copied links) saved to a local file on the phone, so that I can access it later without needing a server.
23. As a user, I want the app to connect to Shizuku and clearly tell me if Shizuku isn't running, so that I know when gesture injection is available.

## Implementation Decisions

### Modules

Six modules make up the Phase 1 prototype:

1. **GestureExecutor** — Accepts a gesture description (type, coordinates/direction, duration) and executes it via Shizuku's InputManager. Encapsulates all MotionEvent/KeyEvent construction, multi-pointer handling, and Shizuku IPC. Consumers call a single `execute(gesture): Result` function.

2. **WorkflowEngine** — Accepts a parsed workflow and produces a stream of step results. Handles sequential execution, loop expansion, delay insertion, variable resolution (loop index), and per-step error policy (retry/skip/stop). This is pure orchestration logic with no Android dependencies — it delegates actual gesture execution and element detection to injected interfaces.

3. **ElementDetector** — Accepts a selector (text, class, image template) and returns the on-screen coordinates of the matching element. Internally chains Accessibility Service lookup → screenshot + OpenCV template match → ML Kit OCR. Consumers see a single `find(selector): Element?` function.

4. **ShizukuBridge** — Thin adapter that manages the Shizuku lifecycle: binding the service, checking/requesting permission, and providing a connected `IInputManager` binder to GestureExecutor. No business logic.

5. **WorkflowRepository** — Reads, writes, and deletes workflow JSON files from the device filesystem. Also stores workflow metadata (name, last run, run count) in a Room database. Thin CRUD layer.

6. **ExecutionLogger** — Records the result of each step execution (step ID, status, duration, error message) into a Room table. Provides queries for the UI to display run history.

### Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **DI**: Hilt
- **Gesture injection**: Shizuku 13.x + Hidden API Bypass
- **Element detection (Phase 1)**: AccessibilityService. Image matching (OpenCV) is Phase 2.
- **Persistence**: Room for metadata/logs, raw JSON files for workflow definitions
- **Serialization**: kotlinx.serialization for JSON workflow parsing
- **Async**: Kotlin Coroutines + Flow
- **Background execution**: Foreground Service with persistent notification

### Workflow JSON format

Workflows are JSON files stored in the app's internal storage. The schema follows the structure established in the grill session — a top-level object with `name`, `variables`, and `steps` array. Each step has `id`, `action`, `params`, optional `on_error`, optional `delay_after_ms`, and optional `output`. The `loop` action nests a `steps` array. Variable references use `$variable_name` syntax.

### Target devices

ROG Phone 6 and Samsung S10e. Minimum API level 28 (Android 9).

### Data storage (Phase 1)

Collected data (e.g. copied links) is saved to a local JSON file on the device. No server integration in Phase 1.

## Testing Decisions

- Only test the **WorkflowEngine** module in Phase 1. It is the highest-value module because it contains all orchestration logic and is pure Kotlin (no Android framework dependencies), making it straightforward to unit test on JVM.
- Good tests verify **external behavior**: given a workflow JSON and mock gesture/detection results, assert the correct sequence of step results, correct loop iteration count, correct error handling policy execution. Do not test internal parsing details or data structure shapes.
- GestureExecutor, ElementDetector, and ShizukuBridge interact with Android system APIs and are tested manually on-device during Phase 1.
- No prior test art exists in the flowbot project skeleton.

## Out of Scope

- GUI workflow builder (Phase 4)
- Server/API integration (Phase 5)
- Cron scheduling and event-based triggers (Phase 4)
- If/else branching, sub-workflow calls, variables beyond loop index (Phase 3)
- Image matching / OpenCV integration (Phase 2)
- OCR / ML Kit integration (Phase 2)
- Multi-device management (Phase 6)
- Publishing to Google Play Store
- iOS support

## Further Notes

- A project skeleton already exists at `flowbot/` in the repo with Gradle build files, version catalog, and Shizuku/Hilt/Compose dependencies pre-configured. Phase 1 implementation should build on this skeleton rather than starting from scratch.
- Shizuku requires the user to start it manually (via ADB or wireless debugging) before the app can inject gestures. The app should detect Shizuku status and guide the user through setup.
- Samsung One UI and ASUS ROG UI may handle gesture injection slightly differently. Device-specific quirks should be isolated behind the GestureExecutor interface.
- Vietnamese text input may require special handling if the active IME doesn't support `AccessibilityNodeInfo.ACTION_SET_TEXT`. Fallback to character-by-character key injection via Shizuku.
