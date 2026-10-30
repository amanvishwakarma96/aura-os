# AURA OS

**AURA = Agentic Unified Runtime for Android**

AURA OS is an Android-compatible, agent-native operating system concept where AI is a first-class system capability rather than a standalone assistant app.

## Goals
- Build on AOSP / Android-compatible foundations.
- Add a privileged AURA Agent Service with explicit policy and permission boundaries.
- Expose safe system actions through a typed Action Framework.
- Support local AI for low-latency tasks and optional remote compute (Ollama / RTX node) for heavy workloads.
- Target Redmi Note 9 (`merlinx`) as the first experimental device after emulator/reference-device validation.

## Repository status
This repository is an **initial engineering scaffold**, not a flashable ROM yet.

Current contents:
- Architecture and security design
- Multi-phase implementation roadmap
- Master AI engineering-agent prompt
- Initial Action Framework data model
- Initial Policy Engine skeleton
- Initial AURA system-service skeleton
- Redmi Note 9 bring-up checklist
- Build/bootstrap helper scripts

## Development strategy
1. Validate AURA framework modules in an AOSP emulator/reference target.
2. Implement safe action execution and policy enforcement.
3. Integrate AI routing and tool calling.
4. Integrate SystemUI / Settings surfaces.
5. Bring up Redmi Note 9 device support.
6. Add signed OTA/recovery and hardening.

## AOSP baseline
Use Google's current `android-latest-release` manifest unless the device bring-up requires a deliberately pinned compatible branch. Never silently change the AOSP baseline; record it in `docs/DECISIONS.md`.

## Safety principles
- LLM output is never trusted as authorization.
- Every action is validated by a deterministic policy layer.
- Sensitive actions require explicit user confirmation and, where appropriate, biometric/device credential verification.
- No agent gets raw unrestricted shell/root execution in production builds.
- Secrets and tokens must never be committed.

See `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, and `agent/AURA_OS_MASTER_AGENT_PROMPT.md`.
