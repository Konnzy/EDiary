# Logging and audit

Application logging uses SLF4J (`@Slf4j`) with Spring Boot's Logback implementation.
Console and file entries include a timestamp with timezone, level, logger,
`requestId`, optional actor ID and optional background job name.

Configuration:

- `EDIARY_LOG_LEVEL`: application level, `INFO` by default.
- `EDIARY_LOG_FILE`: `logs/ediary.log` by default, relative to the backend working directory.
- Files rotate at 10 MB, retain up to 14 days and use at most 200 MB of archives.
- JVM timezone controls timestamps; set `-Duser.timezone=Europe/Kyiv` when needed.

Every request receives `X-Request-ID`. A valid client UUID can be reused; other
values are replaced. CORS exposes this response header to the frontend. Routine
reads are DEBUG, successful mutations INFO, authentication/access/rate-limit
rejections WARN and unexpected failures ERROR. The central handler records a
failure once and returns the request ID. SQL bind/extract logging is disabled.

Exception logs retain types, stack frames, cause chains and SQLState, but omit
exception messages: driver messages can contain SQL values or credentials.
Request bodies, query strings, cookies, authorization headers, passwords, access
codes and reset tokens are not logged by application diagnostics.

`domain_audit` (migration V26) stores actor ID/role, action, target, request ID and
explicitly selected before/after snapshots. It participates in the business
transaction, so rollback removes the audit record as well. The corresponding
technical success event is emitted after commit. Grade, attendance, topic,
homework, schedule and assignment values belong only to this restricted DB table,
not to technical log messages. There is no public audit API. Keep DB/log access
restricted to authorized operators; audit rows have no user FK so user deletion
does not erase history. Existing `admin_activity` remains the admin dashboard feed.

Background jobs get their own request IDs. Password-reset executor tasks inherit
the request MDC and restore the worker's previous context afterward.

Frontend diagnostics keep at most 50 metadata entries in memory and emit safe
console events for network/server failures and runtime errors. They contain no
request payloads, error messages or URL query strings. They are not sent to an
external telemetry service. `AppErrorBoundary` provides a reload action and a
support reference when rendering fails.

Verification: run unit tests with `mvn test`; database integration checks are gated
by `EDIARY_RUN_DB_TESTS=true`. `NotificationPersistenceIntegrationTest` uses an
isolated temporary PostgreSQL schema, mocked email and disabled scheduled jobs.
