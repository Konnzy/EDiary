const workTypeLabels = {
  CLASSWORK: "Класна робота", HOMEWORK: "Домашня робота", INDEPENDENT: "Самостійна робота",
  CONTROL: "Контрольна робота", PRACTICAL: "Практична робота", LABORATORY: "Лабораторна робота",
  THEMATIC: "Тематична", SEMESTER: "Семестрова", YEAR: "Річна",
};

const homeworkStatuses = {
  ASSIGNED: { label: "Призначено", className: "bg-sky-50 text-sky-700" },
  COMPLETED: { label: "Виконано", className: "bg-emerald-50 text-emerald-700" },
  SUBMITTED_LATE: { label: "Виконано із запізненням", className: "bg-amber-50 text-amber-700" },
  OVERDUE: { label: "Прострочено", className: "bg-red-50 text-red-600" },
};

export function formatProgressDate(date) {
  if (!date) return "—";
  const [year, month, day] = date.split("-");
  return `${day}.${month}.${year}`;
}

export function summarizeStudentProgress(subjects = []) {
  const grades = subjects.flatMap((subject) => (subject.grades || []).map((item) => ({
    ...item, subject: subject.name,
  }))).sort((left, right) => right.date.localeCompare(left.date) || right.id - left.id)
    .slice(0, 7).map((item) => ({
      id: item.id, subject: item.subject, type: workTypeLabels[item.workType] || item.workType || "—",
      grade: item.score ?? ({PASSED:"зар.",NOT_PASSED:"не зар.",EXEMPTED:"зв."}[item.result] || "—"), date: formatProgressDate(item.date), comment: item.comment || "—",
    }));

  const homework = subjects.flatMap((subject) => (subject.homework || []).map((item) => ({
    ...item, subject: subject.name,
  }))).sort((left, right) => {
    const priority = { OVERDUE: 0, ASSIGNED: 1, SUBMITTED_LATE: 2, COMPLETED: 3 };
    const priorityDifference = (priority[left.status] ?? 4) - (priority[right.status] ?? 4);
    if (priorityDifference) return priorityDifference;
    if (left.status === "OVERDUE" || left.status === "ASSIGNED") {
      const dueDateDifference = (left.dueDate || "9999-12-31").localeCompare(right.dueDate || "9999-12-31");
      if (dueDateDifference) return dueDateDifference;
    }
    return right.assignedDate.localeCompare(left.assignedDate) || right.id - left.id;
  })
    .slice(0, 3).map((item) => {
      const status = homeworkStatuses[item.status] || { label: item.status || "Призначено", className: "bg-slate-100 text-slate-600" };
      return {
        id: item.id, subject: item.subject, title: item.title,
        completed: item.status === "COMPLETED" || item.status === "SUBMITTED_LATE",
        overdue: item.status === "OVERDUE",
        dueDate: item.dueDate ? `До ${formatProgressDate(item.dueDate)}` : "Без терміну",
        status: status.label,
        statusClass: status.className,
      };
    });

  return { grades, homework };
}
