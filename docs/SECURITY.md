# AURA OS Security Model

## Non-negotiable rules
- Never execute arbitrary shell text produced by an LLM.
- Never grant the model root, `su`, unrestricted Binder, or unrestricted filesystem access.
- Never store credentials in prompts, logs, issue bodies or repositories.
- Treat model responses, remote-node responses and app-provided content as untrusted.
- Use Android Keystore / hardware-backed credentials where available.
- Apply least privilege and SELinux isolation.
- Require explicit approval for sensitive actions.

## Approval levels
- `ALLOW`: no extra confirmation required.
- `CONFIRM`: show exact action and parameters to user.
- `STRONG_CONFIRM`: require biometric/device credential plus explicit approval.
- `DENY`: action is not executable in current context.

## Examples
- Open calculator: ALLOW
- Read notification summary: CONFIRM if private content is included
- Send a message: CONFIRM
- Change device unlock/security: STRONG_CONFIRM or DENY
- Transfer money: AURA must not autonomously complete financial transfer flows; hand off to trusted app and require its own authentication.

## Audit
Audit events should record:
- timestamp
- caller
- action ID
- policy result
- confirmation result
- execution result

Never record passwords, tokens, PINs, full private message bodies, biometric data or encryption keys.
