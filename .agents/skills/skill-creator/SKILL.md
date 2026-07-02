---
name: skill-creator
description: Meta-skill for creating new skills when a needed skill is missing. Use when an existing skill does not cover the current domain or workflow, and a reusable guide would benefit future development.
---

# skill-creator

Guide for creating new skills for the Petty Cash Manager project when existing skills do not cover the required domain or workflow.

## When to run

Invoke this skill whenever:
- A task requires guidance that no existing skill covers.
- A recurring workflow pattern emerges that would benefit from standardization.
- A new technology, tool, or library is introduced to the project.
- An existing skill needs to be split into more focused sub-skills.

## Workflow

### Step 1 — Identify the Gap

Before creating a new skill, verify that the need isn't already covered:

1. Review all existing skills in `.agents/skills/`:
   - `tdd` — Test-Driven Development cycle.
   - `spring-boot` — Backend controllers, services, config, security.
   - `java` — Clean code, design patterns, best practices.
   - `jpa` — Entities, repositories, relationships, queries.
   - `react-typescript` — Components, hooks, types, forms, routing.
   - `docker-compose` — Dockerfiles, compose, volumes, multi-stage builds.
   - `skill-creator` — This meta-skill.

2. If an existing skill partially covers the need, consider **extending** it rather than creating a new one.

3. If the task is too niche for reuse (one-off configuration, project-specific workaround), document it in the project README or a wiki instead.

### Step 2 — Define Skill Scope

A good skill should be:

| Criteria        | Good                                     | Bad                                        |
|----------------|-------------------------------------------|--------------------------------------------|
| **Focused**    | One domain/workflow                       | "Everything about the backend"             |
| **Reusable**   | Applicable to multiple tasks              | Only useful for one specific ticket        |
| **Actionable** | Clear steps to follow                     | Vague principles without steps             |
| **Testable**   | Includes verification steps               | No way to confirm correct execution        |

### Step 3 — Create the Skill File

Create the skill under `.agents/skills/<skill-name>/SKILL.md` following this template:

```markdown
---
name: <skill-name>
description: <One-line description. Explain WHAT and WHEN to use. Max 200 chars.>
---

# <skill-name>

<Brief paragraph explaining the skill's purpose and scope.>

## When to run

Invoke this skill whenever:
- <Trigger condition 1>
- <Trigger condition 2>
- <Trigger condition 3>

## Context

<Optional: describe relevant project files, architecture, or dependencies.>
<Link to specific files using markdown links: [filename](file:///absolute/path)>

## Workflow

### Step 1 — <Action Name>

<Detailed instructions with code examples.>

```<language>
// Example code showing the pattern
```

**Rules:**
1. <Specific rule or convention>
2. <Another rule>

### Step 2 — <Next Action>

<Continue with numbered steps...>

### Step N — Verify

<How to verify the skill was applied correctly.>
```bash
# Verification commands
```
```

### Step 4 — Skill Naming Conventions

| Convention                  | Example                        |
|----------------------------|--------------------------------|
| Lowercase, kebab-case      | `api-testing`                  |
| Domain-specific             | `flyway-migrations`           |
| Technology-focused          | `redis-caching`               |
| Workflow-oriented           | `code-review-checklist`       |

### Step 5 — Quality Checklist

Before finalizing a new skill, verify:

- [ ] **Frontmatter** has `name` and `description` fields.
- [ ] **Description** is under 200 characters and explains when to use.
- [ ] **"When to run"** section has at least 2 trigger conditions.
- [ ] **Workflow** has numbered steps with clear instructions.
- [ ] **Code examples** are project-specific (use actual paths, class names, patterns).
- [ ] **Verification** step exists with concrete commands or checks.
- [ ] **File links** use absolute paths: `[file](file:///absolute/path)`.
- [ ] Skill is **focused** on one domain — split if covering multiple concerns.

### Step 6 — Register and Announce

After creating the skill:
1. Verify the file exists at `.agents/skills/<name>/SKILL.md`.
2. Update this skill's "Identify the Gap" section to include the new skill in the listing.
3. Update the project's Dev Skills Guide if one exists.

### Step 7 — Verify

```bash
# Confirm skill file structure
ls -la .agents/skills/<new-skill-name>/SKILL.md

# Validate frontmatter
head -5 .agents/skills/<new-skill-name>/SKILL.md
# Should show: ---\nname: ...\ndescription: ...\n---
```
