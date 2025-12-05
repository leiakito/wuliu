# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a logistics order and settlement management system with a Vue 3 + TypeScript frontend and Spring Boot 3 backend. The system handles logistics order tracking, user submissions, hardware pricing, and settlement reconciliation with Excel import/export capabilities.

## Architecture

### Backend (Spring Boot 3 + Java 17)
- **Location**: `demo/` directory
- **Framework**: Spring Boot 3.5.7 with MyBatis-Plus
- **Authentication**: Sa-Token for session management
- **Database**: MySQL 8+ with UTF-8MB4
- **Caching**: Caffeine (in-memory) + Redis for distributed caching
- **Key Dependencies**:
  - MyBatis-Plus 3.5.6 for ORM
  - Sa-Token 1.38.0 for authentication
  - Apache POI 5.3.0 for Excel handling
  - SpringDoc OpenAPI for API documentation

### Frontend (Vue 3 + TypeScript)
- **Location**: `front/` directory
- **Framework**: Vue 3.4 with Composition API
- **UI Library**: Element Plus 2.5
- **State Management**: Pinia
- **Routing**: Vue Router 4 with role-based access control
- **Build Tool**: Vite 5

### Domain Structure

Backend follows a modular domain structure under `demo/src/main/java/com/example/demo/`:

1. **auth** - User authentication and management
2. **order** - Logistics order records and Excel import/export
3. **settlement** - Settlement record management with caching
4. **submission** - User submission tracking and logs
5. **hardware** - Hardware pricing management
6. **report** - Dashboard and analytics
7. **log** - System operation logging
8. **common** - Shared utilities, exceptions, and aspects

Each module contains:
- `entity/` - Database entities (MyBatis-Plus)
- `dto/` - Request/response data transfer objects
- `mapper/` - MyBatis-Plus mapper interfaces
- `service/` and `service/impl/` - Business logic
- `controller/` - REST API endpoints

### Key Architectural Patterns

**Session-Based Snapshots**: The `OrderServiceImpl` maintains per-user snapshots of Excel import data to detect changes across imports. Uses `ConcurrentHashMap` with automatic TTL cleanup (2 hours) to prevent memory leaks.

**Settlement Caching**: `SettlementCacheService` uses Redis with proper cache eviction on updates. Handles batch operations efficiently.

**Cell Style Tracking**: `OrderCellStyle` entity tracks Excel cell formatting (background color, font color, strikethrough) separately from order data to detect visual changes.

**Double Billing Prevention**: Settlement service includes warning flags when tracking numbers are submitted multiple times for billing.

## Common Development Commands

### Backend Development

```bash
# Navigate to backend directory
cd demo

# Build (requires Java 17)
mvn clean compile

# Run backend (port 8081)
mvn spring-boot:run

# Build without tests
mvn clean package -DskipTests

# Run with specific Java home (if needed)
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.15/libexec/openjdk.jdk/Contents/Home mvn clean compile
```

### Frontend Development

```bash
# Navigate to frontend directory
cd front

# Install dependencies
npm install

# Run dev server (port 5173, proxies /api to localhost:8081)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

### Docker Deployment

```bash
# Build images
docker compose build

# Start services (frontend on port 8081)
docker compose up -d

# Stop services
docker compose down
```

The Docker setup:
- Backend runs on internal port 8080
- Frontend Nginx serves on port 8081 and reverse proxies `/api` to backend
- Database connection defaults to `host.docker.internal` if `.env` not provided

## Configuration

### Backend Configuration

**Database**: Edit `demo/src/main/resources/application.yml` or use environment variables:
- `APP_DB_URL` - JDBC connection string
- `APP_DB_USERNAME` - Database username
- `APP_DB_PASSWORD` - Database password

**Redis**: Configure via environment variables:
- `REDIS_HOST` (default: localhost)
- `REDIS_PORT` (default: 6379)
- `REDIS_PASSWORD` (optional)
- `REDIS_DATABASE` (default: 0)

**Custom Settings**:
- `app.settlement.warn-double-billing` - Enable/disable double billing warnings
- `app.export.max-rows` - Max rows for Excel export (default: 10000)

### Frontend Configuration

**API Proxy**: `front/vite.config.ts` proxies `/api` requests to `http://localhost:8081` during development.

**Base URL**: API client in `front/src/api/client.ts` uses `/api` as base URL.

### Database Schema

Database initialization scripts are in `demo/src/main/resources/db/`:
- `init.sql` - Schema creation and seed data
- `performance_optimization.sql` - Performance indexes

Key tables:
- `sys_user` - User accounts with BCrypt passwords
- `order_record` - Logistics orders with full-text search indexes
- `order_cell_style` - Excel cell formatting for change detection
- `settlement_record` - Settlement billing records
- `user_submission` - User-submitted tracking numbers
- `user_submission_log` - Submission operation history
- `hardware_price` - Hardware pricing reference
- `sys_log` - Operation audit logs

## Authentication & Authorization

**Backend**: Sa-Token manages sessions with simple-uuid tokens (30-day timeout). All `/api/*` endpoints require authentication except `/api/auth/login`.

**Frontend**:
- Token stored in localStorage (`logistics-token`)
- Axios interceptor attaches token to requests
- Router guards enforce authentication and role-based access
- Two roles: `ADMIN` (full access) and `USER` (limited access)

Role restrictions are defined in route metadata (`front/src/router/index.ts`):
- `/users` and `/logs` - ADMIN only
- Most other routes accessible by both ADMIN and USER

## Excel Import/Export

The system heavily uses Apache POI for Excel operations:

**Import Flow**:
1. Frontend uploads file via `OrderController.importOrders()`
2. Backend parses with `ExcelHelper.parseMultiSheetWorkbook()`
3. Extracts cell styles (colors, strikethrough) into `OrderCellStyle` entities
4. Detects changes by comparing with previous import snapshot (per user)
5. Returns warnings for modified rows based on style/value differences

**Export Flow**:
1. Controller methods return `ResponseEntity<Resource>` with Excel file
2. Frontend detects `application/octet-stream` content-type
3. Triggers browser download with proper filename

**Change Detection**: Uses three strategies:
- By row number (for consecutive imports)
- By tracking number (cross-session)
- Style comparison (background, font color, strikethrough)

## Performance Considerations

**Batch Operations**: Use batch methods for settlements and submissions to minimize database roundtrips.

**Caching**:
- Caffeine cache: 5000 max entries, 30-minute TTL
- Redis cache: Used for settlement data with `@Cacheable` annotations
- Cache eviction: Automatic on updates via `@CacheEvict`

**Connection Pooling**: HikariCP configured with 30 max connections, 10 min idle.

**Full-Text Search**: `order_record` has FULLTEXT index on `tracking_number, sn, model` for keyword searches.

**Prepared Statement Caching**: JDBC URL includes `cachePrepStmts=true&prepStmtCacheSize=250`.

## Important Implementation Details

**Tracking Number Ownership**: `TrackingOwnerService` maintains a JSON file (`demo/data/tracking-owners.json`) mapping tracking numbers to owners. This is loaded at startup and used for assignment.

**Submission Status Flow**: `PENDING` → `CONFIRMED` → processed into settlements.

**Global Exception Handling**: `GlobalExceptionHandler` catches `BusinessException` and returns standardized API responses.

**Operation Logging**: `@LogOperation` annotation on controller methods triggers AOP-based logging to `sys_log` table.

**Security**:
- BCrypt password hashing via Spring Security Crypto
- `ParameterSanitizationFilter` prevents SQL injection
- CORS configured in `WebMvcConfig`

## Frontend State Management

**Pinia Stores**:
- `auth.ts` - User authentication state, token management, profile loading
- Stores are persistent via localStorage

**API Organization**:
- `client.ts` - Axios instance with interceptors
- Separate API modules per domain: `orders.ts`, `settlements.ts`, `submissions.ts`, etc.

**Route Structure**:
- `MainLayout.vue` - Container with navigation for authenticated routes
- All routes under `/` require authentication via route guard
- 404 fallback redirects to `/dashboard`

## Testing

No test configuration is currently set up. When adding tests, note:
- Backend: Use Spring Boot Test with `@SpringBootTest`
- Mockito dependencies are excluded in `pom.xml`
- Frontend: Add Vitest for unit tests

## Troubleshooting

**Port Conflicts**: Backend uses 8081, frontend dev server uses 5173. Ensure these ports are available.

**Database Connection**: If backend fails to connect, verify MySQL is running and credentials match `application.yml`.

**Excel Import Errors**: Check POI version compatibility if encountering parsing issues. Current version is 5.3.0.

**Redis Connection**: System will start without Redis but caching features may not work optimally. Check Redis connection settings.

**Token Expiration**: Tokens expire after 30 days. Client automatically redirects to login on 401 responses.
