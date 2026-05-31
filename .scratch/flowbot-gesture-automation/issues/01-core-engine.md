Status: done

## Parent

file:///home/ubuntu/workspaces2/projects/chrome-dev/.scratch/flowbot-gesture-automation/PRD.md

## What to build

Implement the Core Data Models and Orchestration Engine.
This involves defining the JSON models (Workflow, Step, etc.) and `WorkflowEngine` to handle the execution logic (sequential runs, loops, delays, error handling like retry/skip/stop, and logging callbacks).
The gesture and element detector dependencies will be injected as interfaces and stubbed/mocked for now.

## Acceptance criteria

- [ ] `Workflow` and related data models defined with `kotlinx.serialization` (snake_case JSON support).
- [ ] `WorkflowEngine` processes a sequence of steps, supporting loops (`count`) and delays (`delay_after_ms`).
- [ ] Error policies (`stop`, `skip`, `retry`) are correctly handled.
- [ ] `ExecutionLogCallback` interface is correctly defined and invoked at each step transition.
- [ ] Pure JVM unit tests verify engine behavior.

## Blocked by

None - can start immediately
