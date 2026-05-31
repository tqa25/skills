Status: done

## Parent

file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/PRD.md

## What to build

Implement the Accessibility Element Detector.
Create an `AccessibilityService` (`FlowBotAccessibilityService`) that exposes the live accessibility node tree to the application to find on-screen elements based on text, class name, content description, or resource ID.

## Acceptance criteria

- [ ] `FlowBotAccessibilityService` is defined in the manifest with correct configuration to retrieve window content.
- [ ] `ElementDetector` interface is implemented using depth-first search on the accessibility node tree.
- [ ] Element search correctly filters by text (contains), class name (exact), content description (contains), and resource id.
- [ ] Returns coordinates (Rect/center) of the found elements.
- [ ] Handles timeout logic (e.g., `wait_for_element`).

## Blocked by

- file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/issues/01-core-engine.md
