# AURA OS Architecture

## Layered model

```text
User voice / text / touch
        |
        v
AURA UX surfaces (SystemUI / Assistant / Settings)
        |
        v
AURA Agent Orchestrator
        |
        +--> Intent + Context Resolver
        +--> Model Router (local / remote)
        +--> Planner
        |
        v
AURA Action Framework
        |
        v
AURA Policy & Approval Engine
        |
        +--> allow
        +--> confirm
        +--> biometric/device credential
        +--> deny
        |
        v
Privileged System Service / Android framework APIs
        |
        v
Android platform + HAL + kernel + vendor stack
```

## Core components

### AURA Agent Service
A long-running privileged service responsible for orchestration. It must not directly execute arbitrary model-generated commands.

### Action Framework
All executable capabilities are represented as typed actions with:
- unique action ID
- human-readable description
- risk level
- required permissions/capabilities
- typed parameters
- execution handler
- audit metadata

### Policy Engine
Deterministic decision layer independent of the LLM. Sample risk classes:
- LOW: open app, adjust non-sensitive UI preference
- MEDIUM: read notifications, create reminder
- HIGH: send message, initiate call, modify security-sensitive setting
- CRITICAL: financial, identity, credential, destructive or irreversible operations

### Model Router
Routes requests based on privacy, latency, capability, network, power and cost:
- local small model
- remote Ollama/private server
- optional cloud provider
- optional RTX compute node

### Remote Compute
Optional remote node may provide:
- larger LLM inference
- image/video generation
- computer vision
- 3D rendering
- game/graphics streaming

DLSS/RTX processing stays on compatible remote NVIDIA hardware; the phone receives encoded output/stream.

## Trust boundaries
1. Model output is untrusted input.
2. Action arguments are schema-validated.
3. Policy evaluation occurs before execution.
4. User approval occurs after policy evaluation and before sensitive execution.
5. Action results are minimized before being returned to the model.
6. Audit events contain no secret values.
