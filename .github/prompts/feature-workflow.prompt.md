---
name: feature-workflow
description: Standard workflow for implementing new features.
version: 1.0.0
---

# New Feature Workflow Skill

This skill defines the standard operating procedures for implementing new features in the ERP-System-fuer-Handwerksbetriebe repository.

## 1. Understand the Requirements
- Start by clarifying the acceptance criteria and any related business cases (check `docs/BUSINESS_CASES.md`).

## 2. Design & Architecture
- Ensure the new feature aligns with `docs/ARCHITEKTUR_UEBERSICHT.md`.
- Identify affected components (e.g., Java Backend, `react-pc-frontend`, `react-zeiterfassung`).

## 3. Implementation
- Write modular, well-documented code.
- Apply standard formatting and linting rules.

## 4. Unit Testing
- Write unit tests for new logic in both Java (`src/test/`) and React components.