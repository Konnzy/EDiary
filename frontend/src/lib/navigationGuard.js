let activeGuard = null;
let allowedRoute = null;

export function registerNavigationGuard(guard) {
  activeGuard = guard;

  return () => {
    if (activeGuard === guard) activeGuard = null;
  };
}

export function isRouteNavigationBlocked(nextRoute, currentRoute) {
  if (nextRoute === currentRoute) return false;
  if (allowedRoute === nextRoute) {
    allowedRoute = null;
    return false;
  }

  return activeGuard ? activeGuard({ route: nextRoute }) === false : false;
}

export function isGuardedActionBlocked(action) {
  return activeGuard ? activeGuard({ action }) === false : false;
}

export function allowRouteNavigation(route) {
  allowedRoute = route;
}
