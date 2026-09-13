const API_BASE_URL = (import.meta.env.VITE_API_URL || "http://127.0.0.1:8081/api").replace(/\/$/, "");

let csrfToken = null;
let csrfHeaderName = "X-XSRF-TOKEN";

export class ApiError extends Error {
  constructor(message, { status = 0, code = "REQUEST_FAILED", fieldErrors = {} } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

async function loadCsrfToken() {
  const response = await fetch(`${API_BASE_URL}/security/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new ApiError("Не вдалося встановити захищене з'єднання", { status: response.status });
  }
  const body = await response.json();
  csrfToken = body.token;
  csrfHeaderName = body.headerName || csrfHeaderName;
}

async function request(path, { method = "GET", body, csrf = false } = {}) {
  if (csrf && !csrfToken) {
    await loadCsrfToken();
  }

  const headers = { Accept: "application/json" };
  if (body !== undefined) {
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
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError("Сервер тимчасово недоступний. Спробуйте пізніше.");
  }

  if (response.status === 204) {
    return null;
  }

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
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
    });
  }
  return payload;
}

export const registrationApi = {
  getOptions: () => request("/registration-requests/options"),
  submit: (payload) => request("/registration-requests", { method: "POST", body: payload }),
};

export const authApi = {
  getSession: () => request("/auth/session"),
  forgotPassword: (email) => request("/auth/password/forgot", { method: "POST", body: { email }, csrf: true }),
  resetPassword: (token, newPassword) => request("/auth/password/reset", { method: "POST", body: { token, newPassword }, csrf: true }),
  login: (payload) => request("/auth/login", { method: "POST", body: payload, csrf: true }),
  logout: () => request("/auth/logout", { method: "POST", csrf: true }),
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

export const adminAuthApi = {
  login: (payload) => request("/auth/admin/login", { method: "POST", body: payload, csrf: true }),
  logout: () => request("/auth/admin/logout", { method: "POST", csrf: true }),
};

export const adminRegistrationApi = {
  findAll: ({ status, page = 0, size = 20 } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status);
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
  getDeletionEligibility: (classId) => request(`/admin/classes/${classId}/deletion-eligibility`),
  findStudents: (classId, query = "") => {
    const suffix = query ? `?${new URLSearchParams({ query })}` : "";
    return request(`/admin/classes/${classId}/students${suffix}`);
  },
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
