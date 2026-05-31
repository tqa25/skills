Status: done

## Parent

file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/PRD.md

## What to build

Implement the App UI and Foreground Runner Service.
Build the Jetpack Compose user interface (MainScreen, WorkflowDetailScreen, RunLogScreen) and wire it to the `WorkflowRepository` and `ShizukuBridge`. Create a `WorkflowRunnerService` (Foreground Service) that launches the `WorkflowEngine` and keeps the app running while gestures are automated.

## Acceptance criteria

- [ ] MainScreen lists available workflows and shows visual indicators for Shizuku and Accessibility status.
- [ ] WorkflowDetailScreen shows steps, run history, and has a large "Run" button.
- [ ] `WorkflowRunnerService` displays a persistent notification showing the current running step, acquiring a wake lock to keep screen active or run in background.
- [ ] RunLogScreen displays detailed history of a specific run.

## Blocked by

- file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/issues/01-core-engine.md
- file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/issues/04-local-storage.md
