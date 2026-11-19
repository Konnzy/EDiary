# EDiary

EDiary is a school web application for grades, attendance, timetables, homework, and messaging. The backend uses Java and Spring Boot, the frontend uses React and Vite, and data is stored in PostgreSQL.

## Run with Docker Compose

Start PostgreSQL 18 on the host and create an `ediary` database. Copy `.env.example` to `.env`, set the database connection details, and run this command from the repository root:

```powershell
docker compose up -d --build
```

Flyway applies the database migrations at backend startup. Open the application at [http://127.0.0.1:5173](http://127.0.0.1:5173).

## Run without Docker

Install JDK 25, Node.js 24, and PostgreSQL 18. Create an `ediary` database. Set `EDIARY_DB_URL`, `EDIARY_DB_USERNAME`, and `EDIARY_DB_PASSWORD` in the backend environment using [`backend/.env.example`](backend/.env.example) as a reference.

From the repository root, start the backend in one terminal:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start the frontend in a second terminal:

```powershell
cd frontend
npm ci
npm run dev
```

The frontend runs at [http://127.0.0.1:5173](http://127.0.0.1:5173) and uses the backend API at [http://127.0.0.1:8081/api](http://127.0.0.1:8081/api) by default.

## Demo accounts

After the backend starts, run this command from the repository root to load the demo data into your local database:

```powershell
psql -h localhost -U ediary -d ediary -f backend/src/main/resources/db/seed/dev/seed_demo_data.sql
```

Adjust the database name and username if your local settings differ.

| Role | Username | Password | Admin access code |
| --- | --- | --- | --- |
| Administrator | `admin.test` | `AdminPass!234` | `ABC-123` |
| Teacher | `teacher.test` | `AdminPass!234` | — |
| Student | `student.test` | `AdminPass!234` | — |

These accounts are for local development and demonstrations only.
