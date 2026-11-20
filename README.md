# EDiary

EDiary is a school web application for grades, attendance, timetables, homework, and messaging. The backend uses Java and Spring Boot, the frontend uses React and Vite, and data is stored in PostgreSQL.

## Core stack

![Java 25][java-badge]
![Spring Boot 4.1.1](https://img.shields.io/badge/Spring_Boot-4.1.1-367E22?style=for-the-badge&logo=springboot&logoColor=white&labelColor=367E22)
![PostgreSQL 18](https://img.shields.io/badge/PostgreSQL-18-336791?style=for-the-badge&logo=postgresql&logoColor=white&labelColor=336791)
![React 19.2.6](https://img.shields.io/badge/React-19.2.6-087EA4?style=for-the-badge&logo=react&logoColor=white&labelColor=087EA4)
![Vite 8.3.0](https://img.shields.io/badge/Vite-8.3.0-593DCB?style=for-the-badge&logo=vite&logoColor=white&labelColor=593DCB)
![Tailwind CSS 4.3.3](https://img.shields.io/badge/Tailwind_CSS-4.3.3-0F7285?style=for-the-badge&logo=tailwindcss&logoColor=white&labelColor=0F7285)
![NGINX 1.31.9](https://img.shields.io/badge/NGINX-1.31.9-009639?style=for-the-badge&logo=nginx&logoColor=white&labelColor=009639)

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

[java-badge]: <https://img.shields.io/badge/Java_25-C74634?style=for-the-badge&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPCEtLQpKYXZhIGN1cCBhcnR3b3JrIGZyb20gRGV2aWNvbiAoaWNvbnMvamF2YS9qYXZhLXBsYWluLnN2ZykuCmh0dHBzOi8vZ2l0aHViLmNvbS9kZXZpY29ucy9kZXZpY29uL2Jsb2IvbWFzdGVyL2ljb25zL2phdmEvamF2YS1wbGFpbi5zdmcKClRoZSBNSVQgTGljZW5zZSAoTUlUKQoKQ29weXJpZ2h0IChjKSAyMDE1IGtvbnBhCgpQZXJtaXNzaW9uIGlzIGhlcmVieSBncmFudGVkLCBmcmVlIG9mIGNoYXJnZSwgdG8gYW55IHBlcnNvbiBvYnRhaW5pbmcgYSBjb3B5IG9mCnRoaXMgc29mdHdhcmUgYW5kIGFzc29jaWF0ZWQgZG9jdW1lbnRhdGlvbiBmaWxlcyAodGhlICJTb2Z0d2FyZSIpLCB0byBkZWFsIGluCnRoZSBTb2Z0d2FyZSB3aXRob3V0IHJlc3RyaWN0aW9uLCBpbmNsdWRpbmcgd2l0aG91dCBsaW1pdGF0aW9uIHRoZSByaWdodHMgdG8KdXNlLCBjb3B5LCBtb2RpZnksIG1lcmdlLCBwdWJsaXNoLCBkaXN0cmlidXRlLCBzdWJsaWNlbnNlLCBhbmQvb3Igc2VsbCBjb3BpZXMgb2YKdGhlIFNvZnR3YXJlLCBhbmQgdG8gcGVybWl0IHBlcnNvbnMgdG8gd2hvbSB0aGUgU29mdHdhcmUgaXMgZnVybmlzaGVkIHRvIGRvIHNvLApzdWJqZWN0IHRvIHRoZSBmb2xsb3dpbmcgY29uZGl0aW9uczoKClRoZSBhYm92ZSBjb3B5cmlnaHQgbm90aWNlIGFuZCB0aGlzIHBlcm1pc3Npb24gbm90aWNlIHNoYWxsIGJlIGluY2x1ZGVkIGluIGFsbApjb3BpZXMgb3Igc3Vic3RhbnRpYWwgcG9ydGlvbnMgb2YgdGhlIFNvZnR3YXJlLgoKVEhFIFNPRlRXQVJFIElTIFBST1ZJREVEICJBUyBJUyIsIFdJVEhPVVQgV0FSUkFOVFkgT0YgQU5ZIEtJTkQsIEVYUFJFU1MgT1IKSU1QTElFRCwgSU5DTFVESU5HIEJVVCBOT1QgTElNSVRFRCBUTyBUSEUgV0FSUkFOVElFUyBPRiBNRVJDSEFOVEFCSUxJVFksIEZJVE5FU1MKRk9SIEEgUEFSVElDVUxBUiBQVVJQT1NFIEFORCBOT05JTkZSSU5HRU1FTlQuIElOIE5PIEVWRU5UIFNIQUxMIFRIRSBBVVRIT1JTIE9SCkNPUFlSSUdIVCBIT0xERVJTIEJFIExJQUJMRSBGT1IgQU5ZIENMQUlNLCBEQU1BR0VTIE9SIE9USEVSIExJQUJJTElUWSwgV0hFVEhFUgpJTiBBTiBBQ1RJT04gT0YgQ09OVFJBQ1QsIFRPUlQgT1IgT1RIRVJXSVNFLCBBUklTSU5HIEZST00sIE9VVCBPRiBPUiBJTgpDT05ORUNUSU9OIFdJVEggVEhFIFNPRlRXQVJFIE9SIFRIRSBVU0UgT1IgT1RIRVIgREVBTElOR1MgSU4gVEhFIFNPRlRXQVJFLgotLT48c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgdmlld0JveD0iMCAwIDEyOCAxMjgiPjxwYXRoIGZpbGw9IiNmZmZmZmYiIGQ9Ik00Ny42MTcgOTguMTJjLTE5LjE5MiA1LjM2MiAxMS42NzcgMTYuNDM5IDM2LjExNSA1Ljk2OS00LjAwMy0xLjU1Ni02Ljg3NC0zLjM1MS02Ljg3NC0zLjM1MS0xMC44OTcgMi4wNi0xNS45NTIgMi4yMjItMjUuODQ0IDEuMDkyLTguMTY0LS45MzUtMy4zOTctMy43MS0zLjM5Ny0zLjcxem0zMy4xODktMTAuNDZjLTE0LjQ0NCAyLjc3OS0yMi43ODcgMi42OS0zMy4zNTQgMS42LTguMTcxLS44NDUtMi44MjItNC44MDUtMi44MjItNC44MDUtMjEuMTM3IDcuMDE2IDExLjc2NyAxNC45NzcgNDEuMzA5IDYuMzM2LTMuMTQtMS4xMDYtNS4xMzMtMy4xMzEtNS4xMzMtMy4xMzF6bTExLjMxOS02MC41NzVjLjAwMSAwLTQyLjczMSAxMC42NjktMjIuMzIzIDM0LjE4NyA2LjAyNCA2LjkzNS0xLjU4IDEzLjE3LTEuNTggMTMuMTdzMTUuMjg5LTcuODkxIDguMjY5LTE3Ljc3N2MtNi41NTktOS4yMTUtMTEuNTg3LTEzLjc5MyAxNS42MzQtMjkuNTh6bTkuOTk4IDgxLjE0NHMzLjUyOSAyLjkxLTMuODg4IDUuMTU5Yy0xNC4xMDIgNC4yNzItNTguNzA2IDUuNTYtNzEuMDk1LjE3MS00LjQ1LTEuOTM4IDMuODk5LTQuNjI1IDYuNTI2LTUuMTkyIDIuNzM5LS41OTMgNC4zMDMtLjQ4NSA0LjMwMy0uNDg1LTQuOTUyLTMuNDg3LTMyLjAxMyA2Ljg1LTEzLjc0MiA5LjgxNSA0OS44MjEgOC4wNzYgOTAuODE3LTMuNjM3IDc3Ljg5Ni05LjQ2OHpNODUgNzcuODk2YzIuMzk1LTEuNjM0IDUuNzAzLTMuMDUzIDUuNzAzLTMuMDUzcy05LjQyNCAxLjY4NS0xOC44MTMgMi40NzRjLTExLjQ5NC45NjQtMjMuODIzIDEuMTU0LTMwLjAxMi4zMjYtMTQuNjUyLTEuOTU5IDguMDMzLTcuMzQ4IDguMDMzLTcuMzQ4cy04LjgxMi0uNTk2LTE5LjY0NCA0LjY0NEMxNy40NTUgODEuMTM0IDYxLjk1OCA4My45NTggODUgNzcuODk2em01LjYwOSAxNS4xNDVjLS4xMDguMjktLjQ2OC42MTYtLjQ2OC42MTYgMzEuMjczLTguMjIxIDE5Ljc3NS0yOC45NzkgNC44MjItMjMuNzI1LTEuMzEyLjQ2NC0yIDEuNTQzLTIgMS41NDNzLjgyOS0uMzM0IDIuNjc4LS43MmM3LjU1OS0xLjU3NSAxOC4zODkgMTAuMTE5LTUuMDMyIDIyLjI4NnpNNjQuMTgxIDcwLjA2OWMtNC42MTQtMTAuNDI5LTIwLjI2LTE5LjU1My4wMDctMzUuNTU5Qzg5LjQ1OSAxNC41NjMgNzYuNDkyIDEuNTg3IDc2LjQ5MiAxLjU4N2M1LjIzIDIwLjYwOC0xOC40NTEgMjYuODMzLTI2Ljk5OSAzOS42NjctNS44MjEgOC43NDUgMi44NTcgMTguMTQyIDE0LjY4OCAyOC44MTV6bTI3LjI3NCA1MS43NDhjLTE5LjE4NyAzLjYxMi00Mi44NTQgMy4xOTEtNTYuODg3Ljg3NCAwIDAgMi44NzQgMi4zOCAxNy42NDYgMy4zMzEgMjIuNDc2IDEuNDM3IDU3LS44IDU3LjgxNi0xMS40MzYuMDAxIDAtMS41NyA0LjAzMi0xOC41NzUgNy4yMzF6IiAvPjwvc3ZnPg%3D%3D>
