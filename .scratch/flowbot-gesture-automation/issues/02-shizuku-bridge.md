Status: done

## Parent

file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/PRD.md

## What to build

Implement Shizuku Bridge and Gesture Injection layer.
This slice handles the Android specific gesture execution using the system's `IInputManager` via Shizuku. It manages the Shizuku permission lifecycle and connection state (`ShizukuBridge`) and translates abstract gesture commands (tap, swipe, long press, press home/back/recent, type text) into low-level `MotionEvent` and `KeyEvent` injections.

## Acceptance criteria

- [ ] `ShizukuBridge` class correctly manages Shizuku lifecycle, connection state (Connected/Disconnected/PermissionDenied), and requesting permission.
- [ ] `ShizukuGestureExecutor` implements `GestureExecutor` interface.
- [ ] Touch gestures (tap, long press, swipe, directional swipe) translate to correct `MotionEvent` sequences and are injected.
- [ ] Key events (home, back, recent, type text) are injected correctly.

## Blocked by

- file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/issues/01-core-engine.md
