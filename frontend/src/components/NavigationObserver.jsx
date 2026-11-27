import { useEffect } from "react";
import { useBlocker, useLocation } from "react-router-dom";
import { isRouteNavigationBlocked } from "@/lib/navigationGuard.js";

function routeOf(location) {
  return location.pathname.replace(/^\//, "") + location.search || "login";
}

export function NavigationObserver() {
  const location = useLocation();
  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    isRouteNavigationBlocked(routeOf(nextLocation), routeOf(currentLocation)));
  useEffect(() => {
    if (blocker.state === "blocked") blocker.reset();
  }, [blocker]);
  useEffect(() => {
    window.dispatchEvent(new Event("ediary:route-change"));
  }, [location.pathname, location.search]);
  return null;
}
