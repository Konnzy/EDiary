import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import "./styles.css";
import { readTheme, setTheme } from "./lib/theme.js";
import { AppErrorBoundary } from "./components/AppErrorBoundary.jsx";
import { installDiagnostics, reportRuntimeFailure } from "./lib/diagnostics.js";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { setNavigationRouter } from "./lib/navigation.js";
import { NavigationObserver } from "./components/NavigationObserver.jsx";

setTheme(readTheme());
installDiagnostics();
const router = createBrowserRouter([{
  path: "*",
  element: <><NavigationObserver /><AppErrorBoundary><App /></AppErrorBoundary></>,
}]);
setNavigationRouter(router);

createRoot(document.getElementById("root"), {
  onCaughtError: reportRuntimeFailure,
  onUncaughtError: reportRuntimeFailure,
  onRecoverableError: reportRuntimeFailure,
}).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
);
