Status: done

## Parent

file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/PRD.md

## What to build

Implement Local Storage and Execution Logging using Room.
Set up the Room database to record execution logs and metadata, and implement `WorkflowRepository` to read/write raw JSON workflow files from local storage. Integrate the `ExecutionLogger` with `WorkflowEngine` callbacks to record the result of each step.

## Acceptance criteria

- [ ] Room database configured with `ExecutionLog` and `WorkflowMetadata` entities and DAOs.
- [ ] `WorkflowRepository` can list, read, save, and delete JSON workflow files.
- [ ] `ExecutionLogger` implements `ExecutionLogCallback` and successfully writes run records to Room.
- [ ] App can copy default sample workflows (like the Google News collector) from assets on first launch.

## Blocked by

- file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/issues/01-core-engine.md
