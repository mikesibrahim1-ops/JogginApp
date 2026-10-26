---
inclusion: manual
---

# Output Format — Concise by Default

## Core Rule

ALL responses must be concise. No long explanations, no restating the
request, no narrating your thought process, no repeating code back unless
asked. Do the work, then report it briefly. If the user wants more detail,
they will ask.

## Required End-of-Task Format

Every completed task must end with this exact structure:

**Summary:** One or two sentences max — what was done and the outcome.

**What was done:**

- Bullet points only, one line each
- Files changed/created (with paths)
- Verification proof: commands run + pass/fail result (e.g. "build: passed,
  tests: 12/12 passed, lint: 0 errors") — this line is MANDATORY for any
  code change
- Anything removed/cleaned up

**Up Next:**

Offer 2–3 logical next steps as lettered options, e.g.:

- A) <next step>
- B) <next step>
- C) <next step>

The user can reply with just a letter. Always include the option you'd
recommend first, and mark it (recommended).

## Rules

- Bullet points over paragraphs, always.
- No walls of text. If a response is getting long, cut detail, not facts.
- Code blocks only when the user needs to see/copy the code — never paste
  back code you just wrote unless asked.
- Errors and failures get full detail (this overrides conciseness): what
  failed, why, and what you need.
- Never hide skipped steps behind brevity. If verification couldn't run,
  say so in one bullet — do not omit it.
- "Up Next" options must be genuine, actionable next steps — not filler.
