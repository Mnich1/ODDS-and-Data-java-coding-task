# Stack
Java 21, Maven, JUnit 5 + AssertJ. Plain Java, no frameworks. A library, not an application: no main, no runtime dependencies.
Base package: com.odds.scoreboard

# Commands
- Compile: `./mvnw -q compile`
- All tests: `./mvnw -q test`
- One test class: `./mvnw -q test -Dtest=ClassName`
- One test method: `./mvnw -q test -Dtest=ClassName#methodName`

# Rules
- Work in small steps: one feature per change, then stop so I can review.
- Keep the design minimal: no layers, interfaces, patterns or libraries the task doesn't need yet.
- Use Java 21 idioms: records for immutable data, switch expressions and pattern matching where they help readability.
- Every change comes with tests (JUnit 5 + AssertJ). Run the relevant tests after each change and report pass/fail.
- Never modify, weaken or delete an existing test to make it pass. If a test looks wrong, explain why and ask.
- No new dependencies without asking.
- Money: BigDecimal with an explicit RoundingMode, never double or float.
- Time: inject java.time.Clock. No Thread.sleep in tests: use a fake Clock, CountDownLatch, or Awaitility if it's already on the classpath.
- Shared mutable state must be thread-safe. State the approach in one line in your summary.
- Don't swallow exceptions: no empty catch blocks, no bare `catch (Exception e)`.
- Commit only when I ask.
- Security (OWASP): validate all input at the boundary; reject invalid input, don't "fix" it.
- No shell commands or file paths built from user input.
- Exception messages name the offending values but never contain stack traces or other internals.
- Never log secrets, tokens, passwords or full card numbers. No secrets in code or config files.
- No home-made crypto. Tokens from SecureRandom. Passwords only via a PasswordEncoder (BCrypt/Argon2).
- No frameworks, no Lombok. Pass dependencies through constructors.
- Organise packages by feature/domain, not by technical layer, unless I say otherwise.
- No mocking library: use small hand-written fakes.
- Build must stay clean under `-Xlint:all`: no new compiler warnings.
