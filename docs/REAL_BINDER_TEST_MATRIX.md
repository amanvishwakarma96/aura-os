# Real Binder permission test matrix

**Not executed** — requires AOSP reference emulator build and separate caller processes.

| Case | Setup | Required outcome |
|---|---|---|
| Authorized caller | Trusted signed test client with required permission | UID/PID verified, allowed only for registered capabilities |
| No permission | Separate client lacking signature permission | Denied by service |
| Wrong signature | Client signed with untrusted key | Denied |
| UID/package mismatch | Separate client claiming another package | Caller-supplied identity ignored, denied |
| Multiple packages under UID | Shared UID edge-case where supported | Denied unless explicitly reviewed |
| Cross-user | Managed/secondary user caller | Denied in first revision |
| Caller identity cleared | Deliberate service-side identity-clear mistake test | Test must catch and prevent acceptance |
| Policy version change | Revoked policy epoch before execution | Existing approval invalid |
| Replay | Same approval used twice | First valid execution only |
| Changed arguments | Modify target after approval | Denied |
| SELinux | Unauthorized domain finds/calls service | Denied in enforcing mode |

A mock-only test does not satisfy any real Binder case above. Collect device API level, target SHA, installed APK signatures, logcat, instrumentation output and SELinux denials as evidence.
