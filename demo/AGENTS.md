# Repository Guidelines

## Project Structure & Module Organization
Source lives in `src/main/java/com/example/demo`, split by feature domains (`auth`, `hardware`, `order`, `report`, `settlement`, `submission`). Shared DTOs and utilities stay in `common`; security, Sa-Token, and cache wiring live in `config`. Persisted SQL/XML and migration scripts belong with their module or `src/main/resources/db`. Static files and Thymeleaf templates live in `src/main/resources/static` and `templates`, while `application.yml` carries sane defaults that teams override per profile. Tests mirror this tree under `src/test/java`.

## Build, Test, and Development Commands
- `./mvnw clean package` — builds the runnable Spring Boot jar into `target/`.
- `./mvnw spring-boot:run [-Dspring.profiles.active=dev]` — starts the local API with live reload.
- `./mvnw test` — executes the unit/integration suite; run before pushing.
- `./mvnw verify` — full Maven lifecycle with integration checks; match CI locally.

## Coding Style & Naming Conventions
Stick to Spring Boot defaults: 4-space indentation, UTF-8, and explicit imports. Keep packages under `com.example.demo.<domain>`; classes are PascalCase and beans append their role (`SettlementService`, `OrderMapper`). Use camelCase members, `UPPER_SNAKE_CASE` constants, constructor injection, and Lombok (`@Data`, `@Builder`) for DTOs. REST controllers expose lowercase kebab URLs such as `/api/settlements/{id}`, and MyBatis-Plus mappers live beside their entities.

## Testing Guidelines
Mirror production packages in `src/test/java` and name classes `*Tests`. Use JUnit 5 with `@SpringBootTest` for integrated flows or `@DataJdbcTest` for repositories. Mock remote services, but exercise the in-memory DB when SQL or mapper logic shifts by seeding from `src/main/resources/db`. Assert status codes and payloads, cover failure paths, and avoid tests that only prove the application context boots.

## Commit & Pull Request Guidelines
Commits should be short, imperative, and prefixed with the affected module (`order: add partial export`). Keep one logical change per commit. PRs must spell out user impact, list verification steps (`./mvnw test`, manual curl), and link the tracking issue. Attach screenshots or sample JSON for contract changes and call out any DB/config migration in `db/*.sql`.

## Security & Configuration Tips
Keep secrets out of `application.yml`; load them via environment variables or ignored profile files. When updating Sa-Token, caching, or other security rules in `config`, document the rationale and verify safe defaults remain. Strip sensitive logs before committing and ensure every new endpoint carries the required auth/role annotations from `auth`.
