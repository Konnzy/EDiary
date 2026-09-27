import { useEffect, useState } from "react";
import { ApiError, subjectsApi } from "@/lib/api.js";
import { summarizeStudentProgress } from "@/lib/studentProgressSummary.js";

export function useStudentProgress(role, studentId = "") {
  const [reloadKey, setReloadKey] = useState(0);
  const key = `${role}:${studentId}:${reloadKey}`;
  const [state, setState] = useState({ key: "", grades: [], homework: [], loading: true, error: "" });

  useEffect(() => {
    if (role === "PARENT" && !studentId) return undefined;
    let active = true;
    setState({ key, grades: [], homework: [], loading: true, error: "" });
    const request = role === "PARENT" ? subjectsApi.parent(studentId) : subjectsApi.student();
    request.then((result) => {
      if (active) setState({ key, ...summarizeStudentProgress(result.subjects), loading: false, error: "" });
    }).catch((error) => {
      if (active) setState({ key, grades: [], homework: [], loading: false,
        error: error instanceof ApiError ? error.message : "Не вдалося завантажити оцінки та домашні завдання.",
      });
    });
    return () => { active = false; };
  }, [role, studentId, key]);

  // Hide the previous child's records immediately, before the next effect runs.
  const current = state.key === key ? state : { grades: [], homework: [], loading: role !== "PARENT" || Boolean(studentId), error: "" };
  return { ...current, reload: () => setReloadKey((value) => value + 1) };
}
