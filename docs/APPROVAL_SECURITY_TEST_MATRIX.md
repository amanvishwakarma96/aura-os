# Approval and Binder Security Test Matrix

| Case | Expected outcome | Implemented |
|---|---|---|
| Unknown action | DENY | Framework policy only |
| Caller-supplied LOW risk for high-risk descriptor | Trusted descriptor wins | Framework test |
| Unsupported argument fields | DENY | Framework test |
| Missing required capabilities | DENY | Framework test |
| Untrusted UID/package claims | Reject | No |
| Missing signature permission | Reject | No |
| Altered parameters after approval | Reject | No |
| Replayed approval token | Reject | No |
| Expired approval | Reject | No |
| Different user/profile or UID | Reject | No |
| Strong confirmation spoofed | Reject | No |
| Oversize/malformed parcel | Reject | No |
| SELinux unauthorized service find/call | Deny and log | No |
| Secret redaction | No sensitive payload in audit | No |

Nothing in this table establishes functioning SystemUI approval or a privileged Binder service. It is an implementation gate checklist.
