# Task 04 Proofs — prove the isolation (Policy Simulator matrix + live allow/deny)

## Task Summary

This task demonstrates that the `abreiss-ensemble-terraform` scoping and the permissions
boundary actually hold — first offline with the IAM Policy Simulator, then against real
AWS as the scoped identity. Two evidence files back it:

- `proof/simulate-matrix.{json,txt}` — the offline allow/deny matrix from
  `terraform/bootstrap/simulate-scoping.sh` (`simulate-custom-policy` on the committed
  rendered scoped policy).
- `proof/live-cli-transcript.txt` — the live session: unrelated-resource denials,
  prefixed-resource successes, the boundary-enforcement pair, the OIDC-create explicit
  deny, and full cleanup with a residue sweep.

## What This Task Proves

- **Scoping matrix (Success Metric 2):** for every resource type #9 provisions (S3,
  DynamoDB, ECR, App Runner, Secrets Manager) **and** IAM, an action is allowed on an
  `abreiss-ensemble-*` ARN and denied on the same action against a non-prefixed ARN.
- **Live enforcement (Success Metrics 2, 3, 4):** against real AWS, the scoped identity
  is denied on unrelated resources, succeeds on `abreiss-ensemble-*`, cannot create a
  role without the boundary, can create one with it, and is **explicitly denied**
  `iam:CreateOpenIDConnectProvider`.
- **No residue:** every throwaway resource is deleted and verified gone.
- **Deferred cross-issue gate (Non-Goal 2, Success Metric 6):** "#9's full
  `terraform apply` runs using only this identity" is recorded as deferred to #9.

## Evidence Summary

| Check | Source | Result |
| --- | --- | --- |
| Offline scoping matrix, 6 services × (allow prefixed / deny non-prefixed) | `simulate-matrix.txt` | **12/12 PASS** |
| Live: unrelated bucket list | transcript 4.2a | `AccessDenied` on `s3:ListBucket` |
| Live: non-prefixed bucket create | transcript 4.2b | `AccessDenied` on `s3:CreateBucket` |
| Live: prefixed bucket create + list | transcript 4.2c/d | success |
| Live: create-role WITHOUT boundary | transcript 4.3a | denied (`no identity-based policy allows iam:CreateRole`) |
| Live: create-role WITH boundary + prefixed name | transcript 4.3b | success; `BoundaryArn = …:policy/abreiss-ensemble-boundary` |
| Live: create-OIDC-provider | transcript 4.4 | **explicit deny** in `…:policy/abreiss-ensemble-terraform` |
| Cleanup + residue sweep | transcript 4.5 | role→NoSuchEntity, bucket→NoSuchBucket, sweep→clean |

## Artifact: offline Policy Simulator matrix

**What it proves:** The policy *logic* scopes correctly for every service, independent of
any live account — allowed on `abreiss-ensemble-*`, denied elsewhere.

**Why it matters:** This is the reproducible, account-independent half of Success
Metric 2; anyone can re-run it against the committed JSON without AWS mutations.

**Command / artifact:**

```bash
MODE=custom ./terraform/bootstrap/simulate-scoping.sh
```

**Artifact paths:** `proof/simulate-matrix.txt`, `proof/simulate-matrix.json`

**Result summary:** All 12 checks PASS. `simulate-custom-policy` caps each policy-document
member at 2000 chars, so the ~6 KB scoped policy is split into several ≤1900-char member
documents; IAM evaluates them cumulatively, so Deny-beats-Allow semantics are preserved.
(A `MODE=principal` run against the applied user is available for a live re-check.)

```text
SERVICE        ACTION                        EXPECT   DECISION      RESULT
S3             s3:PutObject                  allowed  allowed       PASS
S3             s3:PutObject                  denied   implicitDeny  PASS
DynamoDB       dynamodb:CreateTable          allowed  allowed       PASS
DynamoDB       dynamodb:CreateTable          denied   implicitDeny  PASS
ECR            ecr:CreateRepository          allowed  allowed       PASS
ECR            ecr:CreateRepository          denied   implicitDeny  PASS
AppRunner      apprunner:UpdateService       allowed  allowed       PASS
AppRunner      apprunner:UpdateService       denied   implicitDeny  PASS
SecretsManager secretsmanager:GetSecretValue allowed  allowed       PASS
SecretsManager secretsmanager:GetSecretValue denied   implicitDeny  PASS
IAM            iam:DeleteRole                allowed  allowed       PASS
IAM            iam:DeleteRole                denied   implicitDeny  PASS
```

## Artifact: live allow/deny CLI transcript

**What it proves:** Against real AWS, the scoped identity is confined to
`abreiss-ensemble-*`, the boundary is enforced (not just applied by convention), and the
OIDC provider is untouchable.

**Why it matters:** Simulation proves logic; this proves AWS actually enforces it on the
applied policy. The boundary pair and the OIDC explicit-deny are acceptance criteria 3
and 4.

**Artifact path:** `proof/live-cli-transcript.txt` (account id redacted; throwaway
resources cleaned up in-run)

**Result summary (key lines):**

```text
4.2a  s3 ls  s3://<unrelated>            → AccessDenied: s3:ListBucket … no identity-based policy allows
4.2b  s3 mb  s3://<non-prefixed>         → AccessDenied: s3:CreateBucket … no identity-based policy allows
4.2c  s3 mb  s3://abreiss-ensemble-test* → make_bucket: … (success)
4.3a  create-role (no boundary)          → AccessDenied: iam:CreateRole … no identity-based policy allows
4.3b  create-role (with boundary+name)   → { BoundaryArn: …:policy/abreiss-ensemble-boundary } (success)
4.4   create-open-id-connect-provider    → AccessDenied: … with an explicit deny in an identity-based
                                            policy: …:policy/abreiss-ensemble-terraform
4.5   delete-role / rb bucket            → get-role: NoSuchEntity; s3 ls: NoSuchBucket; sweep: none (clean)
```

Note the two distinct denial mechanisms captured: **4.3a is an implicit deny** (the
`CreateRole` allow requires the `iam:PermissionsBoundary` condition, so without it no allow
matches), while **4.4 is an explicit deny** (`DenyOidcProviderMutation`) — the stronger
guarantee that an added allow elsewhere cannot override it.

## Deferred cross-issue gate

**Success Metric 6 / Non-Goal 2:** "#9's full `terraform apply` runs successfully using
only the `abreiss-ensemble-terraform` identity" is a **deferred** acceptance gate,
satisfied during issue #9's own implementation. Any permission gap found there is closed
as a narrowly-scoped `abreiss-ensemble-*` addition, never a widening to `*`. This is also
recorded in `docs/AWS_ACCESS.md`.

## Reviewer Conclusion

Both offline and live evidence show the identity is contained to `abreiss-ensemble-*`
across all six resource types, the permissions boundary is enforced at role-creation
time, and the OIDC provider is explicitly protected — with every throwaway test resource
cleaned up and verified gone. The only remaining acceptance criterion (#9's end-to-end
apply) is explicitly deferred to issue #9.
