# EDiary

EDiary is a school web application for grades, attendance, timetables, homework, and messaging. The backend uses Java and Spring Boot, the frontend uses React and Vite, and data is stored in PostgreSQL.

## Core stack

![Java 25](https://img.shields.io/badge/Java-25-C74634?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-367E22?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791?style=for-the-badge&logo=postgresql&logoColor=white)
![React 19.2.6](https://img.shields.io/badge/React-19.2.6-087EA4?style=for-the-badge&logo=react&logoColor=white)
![Vite 8.3.0](https://img.shields.io/badge/Vite-8.3.0-593DCB?style=for-the-badge&logo=vite&logoColor=white)
![Tailwind CSS 4.3.3](https://img.shields.io/badge/Tailwind_CSS-4.3.3-0F7285?style=for-the-badge&logo=tailwindcss&logoColor=white)

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

## License

The original EDiary source code is licensed under the [Mozilla Public License 2.0](LICENSE).
