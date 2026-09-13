import { useEffect, useState } from "react";
import { ApiError, parentApi, scheduleApi } from "@/lib/api.js";

const weekdayByNumber = [null, "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];

export function useTodaySchedule(role, studentId = "") {
  const [state, setState] = useState({ lessons: [], loading: true, error: "" });

  useEffect(() => {
    if (role === "PARENT" && !studentId) {
      setState({ lessons: [], loading: false, error: "" });
      return undefined;
    }

    let active = true;
    setState((current) => ({ ...current, loading: true, error: "" }));

    scheduleApi.findOptions()
      .then((options) => {
        if (!options.academicYears?.includes(options.currentAcademicYear)) return null;
        const period = {
          week: options.currentWeek,
        };
        if (role === "STUDENT") return scheduleApi.findForStudent(period);
        if (role === "TEACHER") return scheduleApi.findForTeacher(period);
        return parentApi.findSchedule(Number(studentId), period);
      })
      .then((schedule) => {
        if (!active) return;
        const weekday = weekdayByNumber[new Date().getDay()];
        const lessons = schedule
          ? schedule.lessons
              .filter((lesson) => lesson.weekday === weekday)
              .sort((left, right) => left.lessonNumber - right.lessonNumber)
          : [];
        setState({ lessons, loading: false, error: "" });
      })
      .catch((error) => {
        if (!active) return;
        setState({
          lessons: [],
          loading: false,
          error: error instanceof ApiError ? error.message : "Не вдалося завантажити розклад.",
        });
      });

    return () => {
      active = false;
    };
  }, [role, studentId]);

  return state;
}

export function formatToday() {
  return new Intl.DateTimeFormat("uk-UA", {
    day: "numeric",
    month: "long",
  }).format(new Date());
}
