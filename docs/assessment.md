# Senior Back-End Engineer Technical Assessment

## Timeframe

**Strict time limit: 2.5 hours**

This assessment covers more ground than can be fully completed in the allotted time. Prioritise what you work on based on what you believe delivers the most value. We want to see how you make trade-off decisions and navigate a realistic, ambiguous problem.

---

## AI Tools

At AllWage, AI-assisted development is part of how we work. We encourage you to use whatever tools help you work effectively—Claude Code, Cursor, GitHub Copilot, ChatGPT, or others.

If you use AI tools, we're interested in:

- How you use them (prompting strategy, iteration, verification)
- When you use them vs. when you don't
- Your judgment about when AI output is ready vs. needs refinement

In the follow-up interview, we may ask you to walk us through your AI interactions and decisions.

---

## Framework & Stack

| Technology  | Requirement                              |
|-------------|------------------------------------------|
| Language    | Java 21+ (LTS)                           |
| Framework   | Spring Boot 3.2.5+                       |
| Persistence | In-memory NoSQL store (document-oriented)|

> **Important:** AllWage uses a NoSQL document database in production. For this assessment, use an in-memory store, but structure your data as documents, not relational tables. Think in terms of access patterns, denormalisation, and document design—not foreign keys and joins.

We encourage you to leverage modern Java features where appropriate. You may use any additional libraries you find useful—document your choices.

---

## The Problem: Geofenced Clock-In System

### Background

AllWage provides workforce management for industries like construction and agriculture. Employees clock in and out of job sites using a mobile app.

**Current state:** We have a working system that receives clock events from the mobile app and stores them in the database. This basic flow works.

**What we're building now:** We want to extend this system to:

- Verify employees are physically at the job site when clocking in (geofencing)
- Send immediate WhatsApp confirmation to the employee (valid or invalid clock-in)
- Stream validated clock data in real-time to a frontend dashboard

### What Already Exists (Starter Project)

Download the starter project: `clockin-assessment-starter.zip`

You'll receive a Spring Boot project with:

| Component        | Status  | Details                                                                               |
|------------------|---------|---------------------------------------------------------------------------------------|
| Clock endpoint   | Working | `POST /api/clocks` receives clock events from mobile app and saves to in-memory store |
| WhatsApp client  | Stub    | Interface to send messages—call it to send confirmations                              |
| In-memory store  | Working | Basic document storage for clock events                                               |

Your job is to extend this existing system with geofencing validation, confirmations, dashboards, and the other requirements below.

### System Context

| Component            | Details                                                                                                                  |
|----------------------|--------------------------------------------------------------------------------------------------------------------------|
| Backend Architecture | Multiple backend instances running concurrently (horizontal scaling). Consider implications for caching, state management, and consistency. |
| Database             | In-memory NoSQL document store. Design your data model around document patterns and access queries—not relational tables. |
| Messaging            | WhatsApp Business API is already integrated. A client interface is provided—just call it.                                |

---

## What You're Given

A PRD (Product Requirements Document) is provided below. This represents what you'd receive from a Product Owner at AllWage.

**Your first task is to write a technical spec before implementing.** This mirrors our actual process.

---

## PRD: Geofenced Clock-In System

### Problem Statement

| | |
|-|-|
| **Who** | Site managers and business owners using AllWage |
| **What** | Need to verify that employees are physically present at job sites when clocking in/out |
| **Why** | Prevents time theft, ensures compliance with labour regulations, and provides audit trails for client billing |

### User Stories

- As a **site manager**, I want to define geofenced areas for my job sites so that clock-ins are only valid within those boundaries.
- As an **employee**, I want to clock in/out from my phone and receive immediate WhatsApp confirmation telling me whether my clock-in was valid or not.
- As a **business owner**, I want to see which clock-ins were inside vs. outside the geofence, and flag anomalies for review.
- As an **auditor**, I want an immutable record of all clock-in attempts, including location data, for compliance purposes.
- As a **site manager**, I want to receive a daily summary via WhatsApp (morning and evening) showing:
  - Who clocked in/out today
  - Any anomalies or failed attempts
  - Current on-site headcount
- As a **business owner**, I want a real-time dashboard that shows clock-ins as they happen across all my sites.

### Constraints

**Mobile App Behaviour:**

- Employees clock in/out via an offline-capable mobile app
- The app queues clock requests locally when offline and syncs when connectivity returns
- Poor connectivity means the app may retry failed requests—your backend may receive the same clock event multiple times
- Each employee receives their clock-in confirmation via WhatsApp (sent to their registered phone number)

**Environmental:**

- Employees are often in areas with poor connectivity (construction sites, farms)
- GPS accuracy varies significantly between devices (can be off by 10–100+ meters)
- Some job sites have multiple valid clock-in zones (e.g., main entrance, equipment yard)
- A single employee might be assigned to multiple sites
- Sites can change location (mobile construction projects)
- **Timezone:** Assume all operations are in SAST (South African Standard Time, UTC+2). Multi-timezone support is out of scope.
- **Geofence shape:** Assume all geofences are circles (centre point + radius). Polygon geofencing is out of scope.

### Temporal Geofences

Geofences have time-based validity:

- **Operating hours:** A geofence might only be active 6am–6pm on weekdays
- **Effective dates:** A geofence boundary is valid from a start date to an end date (for mobile projects that move locations)
- **Zone schedules:** Within a site, different zones may be active at different times (e.g., equipment yard only valid during loading hours 7–9am)

A clock-in is valid only if the employee is within a geofence that is currently active at the time of the clock.

### Validation Rules & Teams

Validation rules can be configured at multiple levels, with inheritance:

**Hierarchy:** Rules cascade from **Site → Team → Employee**. More specific levels override more general ones:

- **Site** defines the default rules for everyone at that location
- **Team** can override site defaults for a group of employees (e.g., "Contractors" have stricter rules)
- **Employee** can override team defaults for individuals (e.g., "John" has an exception)

If no override exists at a level, the parent level's rule applies.

Teams are groups of employees (e.g., "Day Shift", "Night Shift", "Contractors", "Supervisors"). An employee belongs to exactly one team per site.

**Configurable rules:**

| Rule               | Description                                    | Default |
|--------------------|------------------------------------------------|---------|
| Geofence tolerance | Buffer distance added to geofence boundary     | 20m     |
| Strict mode hours  | Tighter tolerance during specified windows     | None    |
| Approval required  | Clock-ins outside primary zone require manager approval | No |

**Example configuration:**

- Site "Construction Alpha": 30m tolerance
- Team "Contractors" at that site: Strict mode always (10m tolerance), manager approval required
- Employee "John" on that team: Extended tolerance (50m) due to parking distance

When validating a clock-in, the system must resolve the effective rules for that specific employee at that specific site at that specific time.

---

## Your Deliverables

### 1. Technical Specification (Required)

Before writing code, write a spec that covers:

- **Problem understanding:** What are the key challenges? What edge cases do you see?
- **Technical approach:** What architecture? What data model? Why?
- **API design:** What endpoints? What contracts?
- **Trade-offs:** What decisions are you making and why?
- **Out of scope:** What are you explicitly NOT solving?
- **Implementation plan:** Given the time constraint, what will you build and in what order?

This spec should be detailed enough that another engineer could implement from it.

**Format:** Markdown file named `SPEC.md`

### 2. Implementation (Required)

Implement core functionality based on your spec. Prioritise what you build based on what you believe is most important.

We expect to see:

- Data model for sites, geofences, employees, and clock-ins (as documents, not relational tables)
- API endpoints for core operations
- Geofence validation logic
- Consideration for multi-instance deployment (caching, consistency)

**Streaming/Real-time:** For the dashboard requirement, implement a streaming endpoint (SSE or WebSocket) that clients can subscribe to for real-time clock-in updates.

We care more about the quality and thoughtfulness of what you build than the quantity.

**Prioritisation Guidance**

We are not expecting you to complete everything. Here's what matters most to us, in order:

1. **Spec quality** — A thoughtful spec that shows you understand the hard parts of this problem. If your spec is shallow, strong implementation won't save you.
2. **Rule hierarchy design** — The Site → Team → Employee inheritance is the most architecturally interesting part. We want to see how you model this and resolve rule conflicts.
3. **Temporal geofence logic** — How you handle time-based validity (operating hours, effective dates) reveals how you think about edge cases.
4. **Core clock-in flow** — Working validation that demonstrates your approach, even if incomplete.
5. **Everything else** — Streaming, WhatsApp integration. Important, but not at the expense of the above.

> If you're running low on time: A great spec with partial, well-tested implementation beats a complete implementation with a shallow spec. We practice test-driven development at AllWage—tests are expected alongside any code you write.

### 3. Tests (Required)

Write tests that demonstrate:

- Your understanding of the problem's edge cases
- How you verify correctness
- What you consider "production-ready"

We're not looking for 100% coverage. We're looking for tests that reveal how you think about verification.

### 4. README (Required)

Your README should include:

**Setup Instructions**
- How to build and run your solution
- Any prerequisites or dependencies

**What You Built**
- What's implemented vs. what's in your spec but not built
- Key architectural decisions and why

**What You'd Do Differently**
- With more time, what would you add or change?
- What did you discover during implementation that would change your spec?

**AI Workflow Deep-Dive**
- Which tools did you use and what was your prompting strategy?
- **Show a prompt that failed:** Describe a prompt that produced unusable output. What went wrong? How many iterations did it take to get something workable?
- **What did you NOT use AI for?:** What did you intentionally do without AI assistance, and why?
- How did you verify AI-generated code was correct before accepting it?

---

## What We're Evaluating

We're not testing your syntax knowledge or how fast you can ship features. We're evaluating whether you can solve backend problems the way a senior engineer at AllWage would.

| What We Assess         | What We're Looking For                                                                                                                |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Prioritisation         | Did you make smart choices about what to build given the time constraint? Did you stick to the 2.5 hour time limit? Overshooting the time tells us you struggle to scope and prioritise. |
| Problem Understanding  | Did you identify the hard parts? Did you ask the right questions (in your spec)? Did you see dimensions we didn't explicitly mention? |
| Architectural Thinking | Does your data model support the stated requirements? Did you make reasonable trade-offs? Can you explain why?                        |
| API Design             | Are your contracts clean and usable? Do they handle failure cases?                                                                    |
| AI-Native Workflow     | Did you use AI effectively? Can you identify when AI output needs human judgment?                                                     |
| Code Quality           | Is your implementation readable, maintainable, and appropriately tested?                                                              |
| Ownership & Communication | Does your README clearly explain your decisions? Would a teammate understand your work?                                            |

---

## Submission

When complete, push your project to a Git repository (GitHub, GitLab, etc.) and share the link.

Ensure your repository includes:

- `SPEC.md` — Your technical specification
- Source code with your implementation
- Tests
- `README.md` — Setup instructions and reflections

---

## Follow-Up Interview

In the technical interview, we'll:

- **Walk through your spec:** What dimensions did you identify? What trade-offs did you make?
- **Review your implementation:** Show us the code you're most proud of, and code you'd improve.
- **Discuss your AI workflow:** Walk us through specific interactions. How did you prompt? How did you verify?
- **Extend the problem:** We'll introduce a new requirement and discuss how you'd approach it.

---

## Questions?

If anything is unclear, document your assumption in your spec and proceed. Part of what we're evaluating is how you handle ambiguity.

---

_Good luck. We're excited to see how you think._

