import { newRequestId, validRequestId, reportApiFailure } from "@/lib/diagnostics.js";

const API_BASE_URL = (import.meta.env.VITE_API_URL || "http://127.0.0.1:8081/api").replace(/\/$/, "");

let csrfToken = null;
let csrfHeaderName = "X-XSRF-TOKEN";

export class ApiError extends Error {
  constructor(message, { status = 0, code = "REQUEST_FAILED", fieldErrors = {}, requestId = null } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.requestId = requestId;
    this.isDiagnosed = true;
  }
}

async function loadCsrfToken() {
  const body = await request("/security/csrf");
  csrfToken = body.token;
  csrfHeaderName = body.headerName || csrfHeaderName;
}

async function request(path, { method = "GET", body, csrf = false, responseType = "json" } = {}) {
  if (csrf && !csrfToken) {
    await loadCsrfToken();
  }

  const requestId = newRequestId();
  const headers = { Accept: "application/json", "X-Request-ID": requestId };
  if (body !== undefined && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }
  if (csrfToken && csrf) {
    headers[csrfHeaderName] = csrfToken;
  }

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      credentials: "include",
      headers,
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    });
  } catch {
    reportApiFailure({ requestId, method, path, status: 0, code: "NETWORK_ERROR" });
    throw new ApiError("Сервер тимчасово недоступний. Спробуйте пізніше.", { requestId, code: "NETWORK_ERROR" });
  }

  if (response.status === 204) {
    return null;
  }
  if (response.ok && responseType === "blob") return response.blob();

  const payload = await response.json().catch(() => ({}));
  const responseId = validRequestId(response.headers.get("X-Request-ID"), validRequestId(payload.requestId, requestId));
  if (!response.ok) {
    reportApiFailure({ requestId: responseId, method, path, status: response.status, code: payload.code || "REQUEST_FAILED" });
    if (response.status === 403 && csrf) {
      csrfToken = null;
    }
    const fallbackMessage = response.status === 401
      ? "Потрібна авторизація. Увійдіть до системи ще раз."
      : response.status === 403
        ? "Недостатньо прав для виконання цієї дії."
        : "Не вдалося виконати запит";
    throw new ApiError(payload.message || fallbackMessage, {
      status: response.status,
      code: payload.code,
      fieldErrors: payload.fieldErrors,
      requestId: responseId,
    });
  }
  return payload;
}

export const registrationApi = {
  getOptions: () => request("/registration-requests/options"),
  submit: (payload) => request("/registration-requests", { method: "POST", body: payload, csrf: true }),
};

export const authApi = {
  getSession: () => request("/auth/session"),
  forgotPassword: (email) => request("/auth/password/forgot", { method: "POST", body: { email }, csrf: true }),
  resetPassword: (token, newPassword) => request("/auth/password/reset", { method: "POST", body: { token, newPassword }, csrf: true }),
  login: (payload) => request("/auth/login", { method: "POST", body: payload, csrf: true }),
  logout: () => request("/auth/logout", { method: "POST", csrf: true }),
};

export const userProfileApi = {
  find: () => request("/users/me"),
  updatePhone: (phone) => request("/users/me/phone", { method: "PATCH", body: { phone }, csrf: true }),
  updateNotifications: (settings) => request("/users/me/notifications", { method: "PUT", body: settings, csrf: true }),
  changePassword: (currentPassword, newPassword) => request("/users/me/password", {
    method: "POST", body: { currentPassword, newPassword }, csrf: true,
  }),
  avatarUrl: (version) => `${API_BASE_URL}/users/me/avatar?v=${version}`,
  uploadAvatar: (file) => {
    const body = new FormData();
    body.append("file", file);
    return request("/users/me/avatar", { method: "POST", body, csrf: true });
  },
  deleteAvatar: () => request("/users/me/avatar", { method: "DELETE", csrf: true }),
};

export const userNotificationApi = {
  feed: () => request("/notifications"),
  markRead: (id) => request(`/notifications/${id}/read`, { method: "POST", csrf: true }),
  markAllRead: () => request("/notifications/read-all", { method: "POST", csrf: true }),
  dismiss: (id) => request(`/notifications/${id}`, { method: "DELETE", csrf: true }),
  clear: () => request("/notifications", { method: "DELETE", csrf: true }),
};

export const messagesApi = {
  list: (folder = "inbox", page = 0) => request(`/messages?${new URLSearchParams({ folder, page: String(page) })}`),
  recipients: (query = "", id = "") => request(`/messages/recipients?${new URLSearchParams({ query, ...(id ? { id: String(id) } : {}) })}`),
  send: (body) => request("/messages", { method: "POST", body, csrf: true }),
  open: (id) => request(`/messages/${id}/open`, { method: "POST", csrf: true }),
  important: (id, important) => request(`/messages/${id}/important`, { method: "PUT", body: { important }, csrf: true }),
};

export const parentApi = {
  findChildren: () => request("/parent/children"),
  findChild: (studentId) => request(`/parent/children/${studentId}`),
  findSchedule: (studentId, { week }) => {
    const params = new URLSearchParams({ week: String(week) });
    return request(`/parent/children/${studentId}/schedule?${params}`);
  },
  linkChild: (code) => request("/parent/children", { method: "POST", body: { code }, csrf: true }),
};

export const scheduleApi = {
  findOptions: () => request("/schedules/options"),
  findForStudent: ({ week }) => {
    const params = new URLSearchParams({ week: String(week) });
    return request(`/student/schedule?${params}`);
  },
  findForTeacher: ({ week }) => {
    const params = new URLSearchParams({ week: String(week) });
    return request(`/teacher/schedule?${params}`);
  },
};

export const teacherJournalApi = {
  options: (academicYear) => request(`/teacher/journal/options${academicYear ? `?${new URLSearchParams({ academicYear })}` : ""}`),
  find: ({ classId, subjectId, month }) => request(`/teacher/journal?${new URLSearchParams({ classId, subjectId, month })}`),
  saveLesson: (scheduleId, date, body) => request(`/teacher/journal/lessons/schedule/${scheduleId}?${new URLSearchParams({ date })}`, {
    method: "PUT", body, csrf: true,
  }),
  saveCell: (lessonId, studentId, body) => request(`/teacher/journal/lessons/${lessonId}/students/${studentId}`, {
    method: "PUT", body, csrf: true,
  }),
  markAllPresent: (scheduleId, date) => request(`/teacher/journal/lessons/schedule/${scheduleId}/attendance?${new URLSearchParams({ date })}`, {
    method: "PUT", csrf: true,
  }),
  clearAttendance: (lessonId) => request(`/teacher/journal/lessons/${lessonId}/attendance`, {
    method: "DELETE", csrf: true,
  }),
};

export const teacherDashboardApi = {
  find: () => request("/teacher/dashboard"),
};

export const teacherStudentsApi = {
  find: () => request("/teacher/students"),
};

export const teacherClassApi = {
  findAll: () => request("/teacher/my-classes"),
  findById: (classId) => request(`/teacher/my-classes/${classId}`),
  findSchedule: (classId, week) => request(`/teacher/my-classes/${classId}/schedule?${new URLSearchParams({ week: String(week) })}`),
};

export const learnerTeachersApi = {
  student: () => request("/student/teachers"),
  parent: (studentId) => request(`/parent/children/${studentId}/teachers`),
};

export const subjectsApi = {
  student: (academicYear) => request(`/student/subjects${academicYear ? `?${new URLSearchParams({ academicYear })}` : ""}`),
  parent: (studentId, academicYear) => request(`/parent/children/${studentId}/subjects${academicYear ? `?${new URLSearchParams({ academicYear })}` : ""}`),
  admin: (studentId, academicYear) => request(`/admin/students/${studentId}/subjects${academicYear ? `?${new URLSearchParams({ academicYear })}` : ""}`),
  homeroom: (classId, studentId, academicYear) => request(`/teacher/my-classes/${classId}/students/${studentId}/subjects${academicYear ? `?${new URLSearchParams({ academicYear })}` : ""}`),
};

export const adminAuthApi = {
  login: (payload) => request("/auth/admin/login", { method: "POST", body: payload, csrf: true }),
  logout: () => request("/auth/admin/logout", { method: "POST", csrf: true }),
};

export const adminRegistrationApi = {
  findNotifications: () => request("/admin/registration-requests/notifications"),
  hideNotification: (id) => request(`/admin/registration-requests/notifications/${id}`, { method: "DELETE", csrf: true }),
  clearNotifications: () => request("/admin/registration-requests/notifications", { method: "DELETE", csrf: true }),
  findAll: ({ status, role, page = 0, size = 20 } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status);
    if (role) params.set("role", role);
    return request(`/admin/registration-requests?${params}`);
  },
  approve: (requestId, login) => request(`/admin/registration-requests/${requestId}/approve`, {
    method: "POST",
    body: { login },
    csrf: true,
  }),
  reject: (requestId, reason) => request(`/admin/registration-requests/${requestId}/reject`, {
    method: "POST",
    body: { reason },
    csrf: true,
  }),
};

  export const adminUsersApi = {
  importTemplate: (format) => request(`/admin/users/import/template?format=${encodeURIComponent(format)}`, { responseType: "blob" }),
  previewImport: (file, format) => {
    const body = new FormData(); body.append("file", file); body.append("format", format);
    return request("/admin/users/import/preview", { method: "POST", body, csrf: true });
  },
  confirmImport: (file, format, fingerprint) => {
    const body = new FormData(); body.append("file", file); body.append("format", format); body.append("fingerprint", fingerprint);
    return request("/admin/users/import/confirm", { method: "POST", body, csrf: true });
  },
  findMe: () => request("/admin/users/me"),
  changeOwnPassword: (currentPassword, newPassword) => request("/admin/users/me/password", {
    method: "POST",
    body: { currentPassword, newPassword },
    csrf: true,
  }),
  searchStudents: (query) => request(`/admin/users/students?${new URLSearchParams({ query })}`),
  create: (payload) => request("/admin/users", {
    method: "POST",
    body: payload,
    csrf: true,
  }),
  findAll: ({ query, role, status, page = 0, size = 20 } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (query) params.set("query", query);
    if (role) params.set("role", role);
    if (status) params.set("status", status);
    return request(`/admin/users?${params}`);
  },
    changeStatus: (userId, status) => request(`/admin/users/${userId}/status`, {
    method: "PATCH",
    body: { status },
      csrf: true,
    }),
    update: (userId, payload) => request(`/admin/users/${userId}`, {
      method: "PATCH",
      body: payload,
      csrf: true,
    }),
    resetPassword: (userId) => request(`/admin/users/${userId}/reset-password`, {
      method: "POST",
      csrf: true,
    }),
  getDeletionEligibility: (userId) => request(`/admin/users/${userId}/deletion-eligibility`),
  delete: (userId) => request(`/admin/users/${userId}`, {
    method: "DELETE",
    csrf: true,
  }),
};

export const adminClassesApi = {
  findAll: ({ query, number, academicYear } = {}) => {
    const params = new URLSearchParams();
    if (query) params.set("query", query);
    if (number) params.set("number", number);
    if (academicYear) params.set("academicYear", academicYear);
    const suffix = params.size ? `?${params}` : "";
    return request(`/admin/classes${suffix}`);
  },
  getOptions: (academicYear) => {
    const suffix = academicYear ? `?${new URLSearchParams({ academicYear })}` : "";
    return request(`/admin/classes/options${suffix}`);
  },
  findById: (classId) => request(`/admin/classes/${classId}`),
  findStatistics: (classId) => request(`/admin/classes/${classId}/statistics`),
  getDeletionEligibility: (classId) => request(`/admin/classes/${classId}/deletion-eligibility`),
  findStudents: (classId, query = "") => {
    const suffix = query ? `?${new URLSearchParams({ query })}` : "";
    return request(`/admin/classes/${classId}/students${suffix}`);
  },
  findStudentParents: (classId, studentId) => request(`/admin/classes/${classId}/students/${studentId}/parents`),
  findUnassignedStudents: (query = "", academicYear = "") => {
    const params = new URLSearchParams();
    if (query) params.set("query", query);
    if (academicYear) params.set("academicYear", academicYear);
    const suffix = params.size ? `?${params}` : "";
    return request(`/admin/classes/students/unassigned${suffix}`);
  },
  create: (payload) => request("/admin/classes", {
    method: "POST",
    body: payload,
    csrf: true,
  }),
  update: (classId, payload) => request(`/admin/classes/${classId}`, {
    method: "PATCH",
    body: payload,
    csrf: true,
  }),
  delete: (classId) => request(`/admin/classes/${classId}`, {
    method: "DELETE",
    csrf: true,
  }),
  addStudent: (classId, studentId) => request(`/admin/classes/${classId}/students/${studentId}`, {
    method: "POST",
    csrf: true,
  }),
  transferStudent: (classId, studentId, targetClassId) => request(`/admin/classes/${classId}/students/${studentId}/class`, {
    method: "PATCH",
    body: { targetClassId },
    csrf: true,
  }),
  removeStudent: (classId, studentId) => request(`/admin/classes/${classId}/students/${studentId}`, {
    method: "DELETE",
    csrf: true,
  }),
};

export const adminSubjectsApi = {
  findAll: (query = "") => {
    const suffix = query ? `?${new URLSearchParams({ query })}` : "";
    return request(`/admin/subjects${suffix}`);
  },
  getOptions: () => request("/admin/subjects/options"),
  create: (payload) => request("/admin/subjects", {
    method: "POST",
    body: payload,
    csrf: true,
  }),
  update: (subjectId, payload) => request(`/admin/subjects/${subjectId}`, {
    method: "PATCH",
    body: payload,
    csrf: true,
  }),
  getDeletionEligibility: (subjectId) => request(`/admin/subjects/${subjectId}/deletion-eligibility`),
  delete: (subjectId) => request(`/admin/subjects/${subjectId}`, {
    method: "DELETE",
    csrf: true,
  }),
};

export const adminScheduleApi = {
  importTemplate: (format) => request(`/admin/schedules/import/template?${new URLSearchParams({ format })}`, { responseType: "blob" }),
  previewImport: ({ classId, academicYear, file, format }) => {
    const body = new FormData();
    for (const [key, value] of Object.entries({ classId, academicYear, file, format })) body.append(key, value);
    return request("/admin/schedules/import/preview", { method: "POST", body, csrf: true });
  },
  confirmImport: ({ classId, academicYear, file, format, fingerprint, decisions }) => {
    const body = new FormData();
    for (const [key, value] of Object.entries({ classId, academicYear, file, format, fingerprint })) body.append(key, value);
    for (const [row, decision] of Object.entries(decisions)) {
      body.append(decision === "replace" ? "replaceRows" : "keepRows", row);
    }
    return request("/admin/schedules/import/confirm", { method: "POST", body, csrf: true });
  },
  findAcademicYears: () => request("/admin/schedules/academic-years"),
  createAcademicYear: (academicYear) => request("/admin/schedules/academic-years", {
    method: "POST",
    body: { academicYear },
    csrf: true,
  }),
  find: ({ classId, academicYear, week }) => {
    const params = new URLSearchParams({
      classId: String(classId),
      academicYear,
      week: String(week),
    });
    return request(`/admin/schedules?${params}`);
  },
  save: ({ classId, academicYear, week, lessons }) => {
    const params = new URLSearchParams({
      classId: String(classId),
      academicYear,
      week: String(week),
    });
    return request(`/admin/schedules?${params}`, {
      method: "PUT",
      body: { lessons },
      csrf: true,
    });
  },
};

export const adminDashboardApi = {
  find: () => request("/admin/dashboard"),
};
