let activeRouter;

export function setNavigationRouter(router) {
  activeRouter = router;
}

export function getRoutePath() {
  return window.location.pathname.replace(/^\//, "") + window.location.search || "login";
}

export function navigate(route, options) {
  if (!activeRouter) throw new Error("Navigation router is not initialized");
  return activeRouter.navigate("/" + route.replace(/^\//, ""), options);
}
