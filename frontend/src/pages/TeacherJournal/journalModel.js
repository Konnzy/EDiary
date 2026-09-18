export const JOURNAL_STORAGE_KEY = "ediary.teacher-journal.prototype.v1";

export const workTypes = [
  { value: "CLASSWORK", label: "Класна робота", shortLabel: "КР" },
  { value: "CONTROL", label: "Контрольна робота", shortLabel: "Контр." },
  { value: "HOMEWORK", label: "Домашня робота", shortLabel: "ДР" },
  { value: "INDEPENDENT", label: "Самостійна робота", shortLabel: "СР" },
  { value: "PRACTICAL", label: "Практична робота", shortLabel: "ПР" },
  { value: "LABORATORY", label: "Лабораторна робота", shortLabel: "ЛР" },
  { value: "THEMATIC", label: "Тематична", shortLabel: "Тем." },
];

export const periods = [
  { value: "2026-09", label: "Вересень 2026" },
  { value: "2026-10", label: "Жовтень 2026" },
  { value: "2026-11", label: "Листопад 2026" },
];

export const journalClasses = [
  {
    id: "9-a",
    label: "9-А",
    students: [
      { id: "bondarenko", name: "Бондаренко Марія" },
      { id: "ivanenko", name: "Іваненко Артем" },
      { id: "ivanov", name: "Іванов Валерій" },
      { id: "petrenko", name: "Петренко Іван" },
      { id: "shevchenko", name: "Шевченко Софія" },
    ],
    subjects: [
      { id: "mathematics", label: "Математика", weekdays: [1, 4] },
      { id: "logic", label: "Логіка", weekdays: [3] },
    ],
  },
  {
    id: "9-b",
    label: "9-Б",
    students: [
      { id: "boiko", name: "Бойко Максим" },
      { id: "koval", name: "Коваль Анна" },
      { id: "melnyk", name: "Мельник Данило" },
      { id: "tkachenko", name: "Ткаченко Олена" },
    ],
    subjects: [
      { id: "mathematics", label: "Математика", weekdays: [2, 5] },
      { id: "informatics", label: "Інформатика", weekdays: [4] },
    ],
  },
];

const weekdayLabels = ["Нд", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"];

export function contextKey(classId, subjectId, period) {
  return `${classId}:${subjectId}:${period}`;
}

export function lessonKey(context, date) {
  return `${context}:${date}`;
}

export function gradeKey(context, studentId, date) {
  return `${context}:${studentId}:${date}`;
}

export function lessonDates(period, weekdays) {
  const [year, month] = period.split("-").map(Number);
  const result = [];
  const date = new Date(year, month - 1, 1, 12);
  while (date.getMonth() === month - 1) {
    if (weekdays.includes(date.getDay())) {
      result.push(toDateId(date));
    }
    date.setDate(date.getDate() + 1);
  }
  return result;
}

export function dateParts(dateId) {
  const date = new Date(`${dateId}T12:00:00`);
  return {
    weekday: weekdayLabels[date.getDay()],
    day: String(date.getDate()).padStart(2, "0"),
    month: String(date.getMonth() + 1).padStart(2, "0"),
  };
}

export function calculateAverage(state, context, studentId, dates) {
  const values = dates
    .map((date) => normalizeGradeEntry(state.grades[gradeKey(context, studentId, date)])?.grade)
    .filter((value) => /^\d+$/.test(value))
    .map(Number);
  if (!values.length) return "—";
  return (values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(1);
}

export function createInitialJournalState() {
  const context = contextKey("9-a", "mathematics", "2026-09");
  const dates = lessonDates("2026-09", [1, 4]);
  const grades = {};
  const sample = [
    ["bondarenko", [10, 11, 9, 10]],
    ["ivanenko", [8, 9, "Н", 10]],
    ["ivanov", [7, 8, 8, 9]],
    ["petrenko", [11, 12, 10, 11]],
    ["shevchenko", [9, 10, 11, 10]],
  ];
  for (const [studentId, values] of sample) {
    values.forEach((value, index) => {
      const legacyStatus = value === "Н" ? "ABSENT" : "PRESENT";
      grades[gradeKey(context, studentId, dates[index])] = {
        grade: /^\d+$/.test(String(value)) ? String(value) : "",
        attendanceStatus: legacyStatus,
        comment: "",
      };
    });
  }

  return {
    grades,
    lessons: {
      [lessonKey(context, dates[0])]: {
        workType: "CLASSWORK",
        topic: "Повторення дій з раціональними числами",
        homework: "Повторити правила, вправи 12-16",
      },
      [lessonKey(context, dates[1])]: {
        workType: "INDEPENDENT",
        topic: "Лінійні рівняння з однією змінною",
        homework: "Параграф 2, вправи 38, 40",
      },
      [lessonKey(context, dates[2])]: {
        workType: "CLASSWORK",
        topic: "Розв'язування текстових задач",
        homework: "Вправи 51-54",
      },
      [lessonKey(context, dates[3])]: {
        workType: "CONTROL",
        topic: "Контрольна робота з теми «Лінійні рівняння»",
        homework: "",
      },
    },
  };
}

export function readJournalState() {
  if (typeof window === "undefined") return createInitialJournalState();
  try {
    const parsed = JSON.parse(window.localStorage.getItem(JOURNAL_STORAGE_KEY));
    if (parsed && typeof parsed.grades === "object" && typeof parsed.lessons === "object") {
      return {
        ...parsed,
        grades: Object.fromEntries(
          Object.entries(parsed.grades).map(([key, entry]) => [key, normalizeGradeEntry(entry)]),
        ),
      };
    }
  } catch {
    // Use the initial prototype when local data is malformed.
  }
  return createInitialJournalState();
}

export function normalizeGradeEntry(entry) {
  if (!entry || typeof entry !== "object") return null;
  if ("grade" in entry || "attendanceStatus" in entry) {
    return {
      grade: /^([1-9]|1[0-2])$/.test(String(entry.grade || "")) ? String(entry.grade) : "",
      attendanceStatus: ["PRESENT", "ABSENT", "EXCUSED", "RELEASED"].includes(entry.attendanceStatus)
        ? entry.attendanceStatus
        : "PRESENT",
      comment: typeof entry.comment === "string" ? entry.comment : "",
    };
  }

  const legacyValue = String(entry.value || "");
  return {
    grade: /^([1-9]|1[0-2])$/.test(legacyValue) ? legacyValue : "",
    attendanceStatus: legacyValue === "Н" ? "ABSENT" : legacyValue === "Зв." ? "RELEASED" : "PRESENT",
    comment: typeof entry.comment === "string" ? entry.comment : "",
  };
}

export function writeJournalState(state) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(JOURNAL_STORAGE_KEY, JSON.stringify(state));
  }
}

function toDateId(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
