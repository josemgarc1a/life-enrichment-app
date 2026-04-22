# Life Enrichment App — Claude Code Instructions

## Project
Spring Boot 3.2.x application for Assisted Living facilities.
Java 17, Maven, PostgreSQL, JWT, AWS S3, Flyway migrations.

## Agent Registry
Before taking any action, read the Agent Registry document in Linear:
Project: "Life Enrichment App" → Documents → "Agent Registry — Configuration & Rules"
Your behavior, rules, and constraints are defined there. Those rules override your defaults.

## Key documents in Linear (always read before starting a task)
- Agent Registry — Configuration & Rules
- Architecture & Data Model
- Agentic Workflow — Operating Manual

## Git workflow
- Always branch from `development`
- Branch naming: feature/{issue-id}-{slug}
- Commit format: feat/test/docs/fix/chore: description
- PRs always target `development`, never `main`
- Never commit directly to `development` or `main`

## Package structure
com.lifeenrichment
├── config/
├── controller/
├── dto/request/ and dto/response/
├── entity/
├── exception/
├── repository/
├── scheduler/
├── security/config/ and security/jwt/
└── service/

## Code rules
- Always use Lombok (@Getter, @Setter, @Builder, @RequiredArgsConstructor, @Slf4j)
- Always use ResponseEntity<> on controllers
- Always add @Operation Swagger annotations on all controller methods
- Always use @Valid on @RequestBody parameters
- Never hardcode secrets or environment-specific values
- Test class naming: {ClassName}Test.java in matching test package

## Running tests
mvn test -Dspring.profiles.active=test

## Label IDs (for Linear updates)
- agent-ready:     7eec2c72-239b-4688-b25d-f7b25ae6508e
- agent-working:   f34b78be-f2ca-4a46-8eb2-c142b98e47d7
- needs-decision:  aa07d784-cde8-449b-a2fc-80a2cd9c0794
- awaiting-review: 45da4b6d-5116-4627-97dc-9c703bdd52cd
- agent-done:      b7d991a7-9f5e-4f27-a797-b8ec8fb89b57
- impl-agent:      83ffd1c9-3d46-468e-8c7b-3d9ed6b17c78
- test-agent:      d2762e75-300e-4f2e-be35-048ffb6c18d5
- pr-agent:        ddc196b1-8456-4765-8db1-c35d91bb9bb2
- docs-agent:      78b757f6-b508-4209-827c-8d21903e7480
- story-agent:     3ae5db2a-25d4-4f54-bf3b-67f5d91599d3
- in-app-agent:    ab166e66-8b81-41f2-8171-a13b84fe8e7a

## Orchestration — sub-agent pipeline

When the user says "implement [issue-id]" or "let's start [story]", run the full
pipeline automatically using the Agent tool. Do NOT ask for confirmation between
phases. The user only re-enters the flow at `awaiting-review`.

### Pipeline order
1. **impl-agent**  — writes all production code files, commits, pushes branch
2. **test-agent**  — reads produced files, writes all tests, runs mvn test, commits, pushes
3. **pr-agent**    — opens PR targeting `development`, updates Linear to In Review / awaiting-review

### Linear updates at each handoff
- Before impl-agent starts  → set labels: impl-agent + agent-working, state: In Progress
- Before test-agent starts  → set labels: test-agent + agent-working
- Before pr-agent starts    → set labels: pr-agent + agent-working
- After PR is open          → set labels: pr-agent + awaiting-review, state: In Review

### Sub-agent prompt contract
Each sub-agent receives a self-contained prompt that includes:
- The Linear issue ID, title, description, and acceptance criteria
- The working directory and git branch name
- The output of the previous agent (file list for test-agent; branch + coverage for pr-agent)
- All code rules and project conventions from this file
- A clear instruction to return a structured result: { files_produced, branch, summary }

### Escalation rule
If any sub-agent encounters ambiguity or a blocking error, the orchestrator sets
`needs-decision` on the Linear issue and reports to the user immediately. It does NOT
proceed to the next agent phase.