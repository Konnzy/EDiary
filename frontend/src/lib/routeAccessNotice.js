export const ROUTE_ACCESS_NOTICE_EVENT = "ediary:route-access-notice";

const STORAGE_KEY = "ediary.route-access-notice";

export const routeAccessMessages = {
  AUTH_REQUIRED: "Для перегляду цієї сторінки необхідно увійти в систему.",
  ACCESS_DENIED: "У вас недостатньо прав для перегляду цієї сторінки.",
  SESSION_CHECK_FAILED: "Не вдалося перевірити сесію. Спробуйте увійти повторно.",
};

export function showRouteAccessNotice(type) {
  if (!routeAccessMessages[type]) return;

  window.sessionStorage.setItem(STORAGE_KEY, type);
  window.dispatchEvent(new Event(ROUTE_ACCESS_NOTICE_EVENT));
}

export function consumeRouteAccessNotice() {
  const type = window.sessionStorage.getItem(STORAGE_KEY);
  window.sessionStorage.removeItem(STORAGE_KEY);

  return routeAccessMessages[type] ? { type, message: routeAccessMessages[type] } : null;
}
