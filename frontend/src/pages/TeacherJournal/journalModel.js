export const workTypes = [
  { value: "CLASSWORK", label: "Класна робота" },
  { value: "CONTROL", label: "Контрольна робота" },
  { value: "HOMEWORK", label: "Домашня робота" },
  { value: "INDEPENDENT", label: "Самостійна робота" },
  { value: "PRACTICAL", label: "Практична робота" },
  { value: "LABORATORY", label: "Лабораторна робота" },
  { value: "THEMATIC", label: "Тематична" },
  { value: "SEMESTER", label: "Семестрова" },
  { value: "YEAR", label: "Річна" },
];

export const homeworkStatuses = [
  { value: "", label: "Не вказано" },
  { value: "ASSIGNED", label: "Призначено" },
  { value: "OVERDUE", label: "Прострочено" },
  { value: "COMPLETED", label: "Виконано" },
  { value: "SUBMITTED_LATE", label: "Із запізненням" },
];

export function normalizeLessonForm(lesson) {
  return {
    workType: workTypes.some((type) => type.value === lesson?.workType) ? lesson.workType : "CLASSWORK",
    topic: lesson?.topic ?? "",
    homework: lesson?.homework ?? "",
    homeworkDeadline: lesson?.homeworkDeadline ?? "",
  };
}

const weekdayLabels = ["Неділя", "Понеділок", "Вівторок", "Середа", "Четвер", "П'ятниця", "Субота"];

export function dateParts(dateId) {
  const date = new Date(`${dateId}T12:00:00`);
  return {
    weekday: weekdayLabels[date.getDay()],
    day: String(date.getDate()).padStart(2, "0"),
    month: String(date.getMonth() + 1).padStart(2, "0"),
  };
}
