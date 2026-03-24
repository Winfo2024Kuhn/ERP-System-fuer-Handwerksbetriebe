---
name: pre-merge-tests
description: Standard checklist for pre-merge testing and compilations.
version: 1.0.0
---

# Pre-Merge Testing Skill

Before any code is merged in the ERP-System-fuer-Handwerksbetriebe repository, the following automated steps MUST be successful:

## A. Backend (Java)
- Run Maven Build: `./mvnw clean package -DskipTests`
- Run Backend Tests: `./mvnw test`

## B. Frontend - PC Version (`react-pc-frontend`)
- Install Dependencies (if changed): `cd react-pc-frontend && npm install`
- Type Check & Lint: `cd react-pc-frontend && npm run lint`
- Build Frontend: `cd react-pc-frontend && npm run build`
- Run Tests: `cd react-pc-frontend && npm run test`

## C. Frontend - Zeiterfassung (`react-zeiterfassung`)
- Install Dependencies (if changed): `cd react-zeiterfassung && npm install`
- Type Check & Lint: `cd react-zeiterfassung && npm run lint`
- Build Frontend: `cd react-zeiterfassung && npm run build`
- Run Tests: `cd react-zeiterfassung && npm run test`

## D. Full System Check
- Start the test environment using Docker: `docker-compose up -d` or via `start-docker.bat`.
- Ensure the API and both frontends communicate correctly without errors.