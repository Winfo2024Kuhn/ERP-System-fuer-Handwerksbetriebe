---
name: bugfix-workflow
description: Standard workflow for fixing bugs.
version: 1.0.0
---

# Bug Fix Workflow Skill

This skill defines the standard operating procedures for fixing bugs in the ERP-System-fuer-Handwerksbetriebe repository.

## 1. Reproduce & Isolate
- Identify the exact component and steps to reproduce the bug. Check `logs/` if applicable.

## 2. Root Cause Analysis
- Document why the bug occurred before fixing it.

## 3. Fix
- Implement the minimal effective change to resolve the issue without breaking existing functionality.

## 4. Regression Testing
- Add a test case that specifically targets the fixed bug to prevent future regressions.