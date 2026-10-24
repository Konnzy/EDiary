import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import "./styles.css";
import { readTheme, setTheme } from "./lib/theme.js";
import { AppErrorBoundary } from "./components/AppErrorBoundary.jsx";
import { installDiagnostics, reportRuntimeFailure } from "./lib/diagnostics.js";

setTheme(readTheme());
installDiagnostics();

createRoot(document.getElementById("root"), {
  onCaughtError: reportRuntimeFailure,
  onUncaughtError: reportRuntimeFailure,
  onRecoverableError: reportRuntimeFailure,
}).render(
  <React.StrictMode>
    <AppErrorBoundary><App /></AppErrorBoundary>
  </React.StrictMode>,
);
