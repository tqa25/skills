# FlowBot Domain Glossary

This file defines the shared vocabulary and domain context for the FlowBot project. Agents must use these terms consistently across code, issues, and documentation.

## Core Concepts

* **Workflow**: A sequence of automated actions defined in a JSON file.
* **Step**: A single unit of work within a workflow (e.g., tap, swipe, delay, loop).
* **Action**: The specific operation a step performs (e.g., `tap`, `long_press`, `wait_for_element`).
* **Error Policy**: The rule determining what happens when a step fails (`stop`, `skip`, or `retry`).
* **Selector (ElementSelector)**: A set of criteria (text, class name, resource ID, content description) used to locate a specific UI element on the screen via the Accessibility Service.
* **Shizuku**: The underlying service used to inject raw `MotionEvent` and `KeyEvent` at the system level without root access.
* **Accessibility Node Tree**: The hierarchical representation of the UI provided by Android's AccessibilityService, used here purely for element detection (not gesture execution).
* **Execution Log**: A database record tracking the success, failure, or skipped status of every step execution, along with duration and error messages.

## Architectural Boundaries

* **WorkflowEngine**: Pure Kotlin orchestrator. Has no Android framework dependencies. Delegates all external interactions to injected interfaces.
* **GestureExecutor**: Interface for Android gesture injection. Implementation relies on Shizuku's `IInputManager`.
* **ElementDetector**: Interface for finding UI elements. Implementation relies on Android `AccessibilityService`.

## Constraints & Decisions (Phase 1)
* **No Root**: The app must use Shizuku, never root (`su`).
* **No Image Matching**: Phase 1 relies strictly on Accessibility nodes for finding elements. OpenCV/Image matching is out of scope.
* **Local Storage Only**: Workflows and logs are saved on the device. No server integration.
