# AURA OS Roadmap

## Phase 0 — Engineering foundation
- Repository conventions
- Build-host validation
- AOSP baseline decision
- Threat model
- CI checks for standalone AURA modules
- Emulator/reference target first

**Exit:** documented reproducible source checkout and baseline build instructions.

## Phase 1 — AURA Action Framework
- Typed action contracts
- Registry
- Parameter validation
- Policy decisions
- Confirmation contract
- Audit events
- Unit tests

**Exit:** actions cannot execute without registry + policy approval.

## Phase 2 — AURA Agent Service
- Privileged system service
- Binder/AIDL API
- caller identity validation
- action dispatch
- lifecycle/boot registration
- structured logs

**Exit:** trusted system client can request a safe test action end-to-end.

## Phase 3 — AI runtime
- intent parser
- tool/action schema exporter
- local inference adapter
- remote Ollama adapter
- model routing policy
- network/privacy guardrails

**Exit:** natural-language request maps to typed action proposal; model never bypasses policy.

## Phase 4 — AURA UX & SystemUI
- assistant overlay
- command palette
- approval sheets
- AI-aware notification summaries
- AURA settings
- audit/activity screen

**Exit:** core flow usable without developer shell commands.

## Phase 5 — Core system actions
- open app
- safe settings actions
- flashlight
- Bluetooth/Wi-Fi requests where platform policy permits
- call/contact resolution
- message drafting (sending requires confirmation)
- reminders
- notification summaries

**Exit:** useful daily agent workflows with deterministic approval rules.

## Phase 6 — Redmi Note 9 (`merlinx`) bring-up
- bootloader/recovery plan
- exact hardware variant verification
- kernel/device/vendor source review
- boot
- display/touch
- Wi-Fi/Bluetooth
- telephony/RIL/data
- audio
- sensors
- camera
- fingerprint
- GPS
- encryption
- SELinux enforcing
- power/thermal

**Exit:** stable daily-test build. Do not call it production-ready.

## Phase 7 — Remote Compute / RTX
- authenticated node discovery
- encrypted transport
- capability negotiation
- Ollama inference
- streaming protocol integration
- remote rendering hooks
- wake/reconnect behavior

**Exit:** optional remote tasks work without weakening local device security.

## Phase 8 — Update, recovery & hardening
- signing keys strategy
- AVB/verified boot strategy
- OTA packaging
- rollback strategy
- recovery documentation
- SELinux policies
- fuzz/security testing
- dependency/SBOM process

## Phase 9 — Compatibility & release engineering
- CTS/VTS-inspired validation where applicable
- battery/performance benchmarks
- privacy review
- reproducible release notes
- release channels: dev / alpha / beta
