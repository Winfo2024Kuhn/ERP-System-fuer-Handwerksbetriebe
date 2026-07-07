# Contributing to ERP System

First off, thanks for taking the time to contribute! 🎉

## How Can I Contribute?

### Reporting Bugs

- Check if the issue already exists before creating a new one
- Use a clear, descriptive title
- Include steps to reproduce the bug
- Mention your environment (OS, Java version, Docker version)

### Suggesting Enhancements

- Use a clear, descriptive title
- Explain the use case and why the enhancement would be useful
- Be open to discussion and feedback

### Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests if applicable (`mvn test`)
5. Commit with a clear message (`git commit -m "Add: amazing feature"`)
6. Push to your fork (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## Code Style

- Follow the existing code style in the project
- Use meaningful variable and method names
- Write comments for complex logic
- Keep methods focused and concise

## Development Setup

### Prerequisites

- Java 23+
- Maven 3.9+
- Node.js 22+ (for frontend)
- Docker & Docker Compose (recommended)

### Quick Start with Docker

```bash
git clone https://github.com/Winfo2024Kuhn/ERP-System-fuer-Handwerksbetriebe.git
cd ERP-System-fuer-Handwerksbetriebe
docker compose up
```

Then open http://localhost:8080

### Local Development

```bash
# Backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Frontend (in a separate terminal)
cd react-pc-frontend
npm install
npm run dev
```

## Questions?

Feel free to open an issue with the `question` label if you need help.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
