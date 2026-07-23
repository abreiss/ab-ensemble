# Task 01 Proofs — `User` record, `UserRepository`, and `PasswordHasher` (data + crypto foundation)

## Task Summary

This task proves the persistence + hashing foundation that every later user-accounts unit builds on is in place and correct: a `User` `@DynamoDbBean` keyed on a **normalized email**, a `UserRepository` whose `create` enforces email uniqueness **atomically** at the datastore (not via a read-then-write race), and a bcrypt `PasswordHasher` (work factor 12) added through the standalone `spring-security-crypto` jar — with **no** Spring Security auto-config or filter chain activated. There is no user-facing surface yet; the proof is via tests, coverage, and the cloud table declaration.

## What This Task Proves

- **Salted bcrypt at rest.** `PasswordHasher` hashes with a per-call random salt (same input → different hash) and verifies both the right- and wrong-password cases; the raw password is never stored.
- **Email is the identity key, normalized.** `User.setEmail` trims + lowercases, so `Foo@X.com` and `foo@x.com` resolve to one account; the bean carries `userId`/`passwordHash`/`createdAt` and never echoes the hash (no `toString`).
- **Atomic uniqueness guard.** `UserRepository.create` uses a conditional put (`attribute_not_exists(email)`); a duplicate surfaces as `DuplicateEmailException` with no silent overwrite — verified against real DynamoDB Local.
- **userId lookup for `/api/me`.** `findByUserId` is a demo-scale full scan (no GSI), returning the account or empty (incl. the null guard).
- **Cloud table declared, no IAM diff.** `aws_dynamodb_table.users` (hash `email`, `PAY_PER_REQUEST`) is added; `fmt`/`validate` pass; the existing `table/${prefix}-*` instance-role grant already covers it.
- **The crypto jar is inert.** Only `spring-security-crypto` resolves — no `spring-security-core/config/web` — so the hand-rolled `SessionAuthFilter` is untouched.

## Evidence Summary

- `./gradlew test -PskipFrontend --tests 'com.ensemble.user.*'` → **BUILD SUCCESSFUL** (12 tests across `PasswordHasherTest`, `UserTest`, `UserRepositoryIT`).
- JaCoCo: the four new `com.ensemble.user` classes are **100% line** and **100% branch** (where branches exist), covering the two critical-logic paths this task owns — `PasswordHasher.matches` (both boolean outcomes) and the duplicate-email conditional in `create` (success + conditional-fail).
- `terraform fmt -check -recursive` clean and `terraform validate` = Success for the new `aws_dynamodb_table.users`.
- Source inspection confirms `passwordHash` is never logged and `User` has no field-echoing `toString`.

## Artifact: User-package test suite (bcrypt, normalization, atomic uniqueness)

**What it proves:** The data/crypto layer behaves per spec Unit 1 — including the DynamoDB Local round-trip and the atomic `attribute_not_exists(email)` guard.

**Why it matters:** These are the backend-domain behaviors that must hold before any controller or token work can depend on a `User`.

**Command:**

```bash
./gradlew test -PskipFrontend --tests 'com.ensemble.user.*'
```

**Result summary:** BUILD SUCCESSFUL; all 12 tests green.

```
com.ensemble.user.PasswordHasherTest:
  - hashVerifiesAgainstRawPassword()
  - wrongPasswordDoesNotMatch()
  - sameInputProducesDifferentSalts()
com.ensemble.user.UserTest:
  - emailIsNormalizedToLowercaseTrimmed()
  - normalizeEmail_isNullSafe()
com.ensemble.user.UserRepositoryIT:
  - createThenFindByEmail()
  - findByEmail_whenMissing_returnsEmpty()
  - create_duplicateEmail_throwsDuplicateEmailException()
  - findByEmail_isCaseAndSpaceInsensitive()
  - findByUserId_returnsUser()
  - findByUserId_whenMissing_returnsEmpty()
  - findByUserId_whenNull_returnsEmpty()
```

## Artifact: JaCoCo coverage of the new domain classes

**What it proves:** ≥90% line and 100% branch on the task's critical logic (bcrypt verification path, duplicate-email conditional).

**Why it matters:** `AGENTS.md`/`docs/TESTING.md` require 100% branch on critical backend-domain logic; this is the machine-checked evidence.

**Command:**

```bash
./gradlew test -PskipFrontend jacocoTestReport
# parsed from build/reports/jacoco/test/jacocoTestReport.xml
```

**Result summary:** All four new classes are 100% line and 100% branch. `PasswordHasher.matches`/`create` have no in-method branches (the branching lives inside bcrypt / the SDK), and both outcomes of each are exercised by tests.

```
DuplicateEmailException  line=2/2=100%    branch=n/a
PasswordHasher           line=4/4=100%    branch=n/a   (matches: both true/false outcomes tested)
UserRepository           line=20/20=100%  branch=2/2=100%
User                     line=14/14=100%  branch=2/2=100%  (normalizeEmail null/non-null)
```

## Artifact: Cloud users table declared, no IAM change

**What it proves:** The deploy-time DynamoDB users table is declared and the Terraform is valid + formatted.

**Why it matters:** The dev table is auto-created by `DynamoDbTableInitializer`; the cloud table must be Terraform-owned, and this confirms it needs no new IAM (the existing prefix wildcard covers it).

**Command:**

```bash
terraform -chdir=terraform/deploy fmt -check -recursive
terraform -chdir=terraform/deploy validate
```

**Result summary:** `fmt -check` clean; `validate` = "Success! The configuration is valid" (the only warning is a local `dev_overrides` CLI setting, unrelated to this change). The added resource:

```hcl
resource "aws_dynamodb_table" "users" {
  name         = "${local.prefix}-users"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "email"
  attribute {
    name = "email"
    type = "S"
  }
}
```

## Artifact: `spring-security-crypto` is a plain utility jar (no auto-config)

**What it proves:** Adding bcrypt did not pull in the Spring Security starter, so no auto-config/filter chain activates and the hand-rolled `SessionAuthFilter` is unaffected.

**Why it matters:** Spec + task 1.1 explicitly require the crypto jar alone; a stray full-security dependency would silently change request handling.

**Command:**

```bash
./gradlew dependencies --configuration runtimeClasspath | grep -i spring-security
```

**Result summary:** Exactly one line — the crypto jar, BOM-versioned. No `spring-security-core/config/web`.

```
+--- org.springframework.security:spring-security-crypto -> 7.1.0
```

## Artifact: password hash never logged or serialized

**What it proves:** `passwordHash` cannot leak into logs or a `toString`.

**Why it matters:** Spec Success Metric #4 (passwords never appear in logs/responses).

**Command:**

```bash
grep -rn "toString" src/main/java/com/ensemble/user/     # only a Javadoc note; no override
grep -rniE "log.*(passwordhash|password)" src/main/java/com/ensemble/   # none
```

**Result summary:** `User` defines no `toString()` (default identity string never echoes fields); no log statement references the hash or a raw password.

## Reviewer Conclusion

The data + crypto foundation is complete and correct: bcrypt(12) hashing with salted storage, an email-normalized `User` bean, an atomically-unique `UserRepository.create`, and a Terraform-declared cloud table with no IAM change — all green with 100% line/branch on the critical logic, and the crypto dependency proven inert against the existing filter. Ready for Task 2.0 (identity-bearing token + login + `/api/me`).
