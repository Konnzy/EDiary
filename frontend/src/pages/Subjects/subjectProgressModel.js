export const subjectProgress = [
  {
    id: "mathematics",
    name: "Математика",
    teacher: "Коваленко Олександр Іванович",
    previousAverage: 8.4,
    grades: [
      { id: 1, date: "17.09.2026", workType: "Самостійна робота", grade: 10, comment: "Добре засвоєно рівняння" },
      { id: 2, date: "14.09.2026", workType: "Домашня робота", grade: 9, comment: "" },
      { id: 3, date: "10.09.2026", workType: "Контрольна робота", grade: 8, comment: "Звернути увагу на останню задачу" },
      { id: 4, date: "07.09.2026", workType: "Класна робота", grade: 10, comment: "" },
      { id: 5, date: "03.09.2026", workType: "Домашня робота", grade: 9, comment: "" },
      { id: "m-2026-05", date: "25.05.2026", workType: "Підсумкова контрольна", grade: 9, comment: "" },
      { id: "m-2026-03", date: "12.03.2026", workType: "Самостійна робота", grade: 8, comment: "" },
      { id: "m-2025-12", date: "15.12.2025", workType: "Контрольна робота", grade: 9, comment: "Стабільний результат" },
      { id: "m-2025-09", date: "20.09.2025", workType: "Класна робота", grade: 8, comment: "" },
      { id: "m-2025-05", date: "22.05.2025", workType: "Підсумкова контрольна", grade: 8, comment: "" },
      { id: "m-2024-12", date: "18.12.2024", workType: "Контрольна робота", grade: 7, comment: "" },
      { id: "m-2024-09", date: "24.09.2024", workType: "Самостійна робота", grade: 8, comment: "" },
    ],
    attendance: [
      { id: 1, date: "17.09.2026", topic: "Квадратні рівняння", status: "PRESENT" },
      { id: 2, date: "14.09.2026", topic: "Формула коренів квадратного рівняння", status: "PRESENT" },
      { id: 3, date: "10.09.2026", topic: "Контрольна робота", status: "ABSENT" },
      { id: 4, date: "07.09.2026", topic: "Неповні квадратні рівняння", status: "PRESENT" },
      { id: 5, date: "03.09.2026", topic: "Повторення лінійних рівнянь", status: "EXCUSED" },
      { id: 6, date: "01.09.2026", topic: "Вступний урок", status: "PRESENT" },
      { id: "ma-2026-05", date: "25.05.2026", topic: "Підсумкове повторення", status: "PRESENT" },
      { id: "ma-2026-03", date: "12.03.2026", topic: "Системи рівнянь", status: "ABSENT" },
      { id: "ma-2025-12", date: "15.12.2025", topic: "Контрольна робота", status: "PRESENT" },
      { id: "ma-2025-09", date: "20.09.2025", topic: "Функції", status: "PRESENT" },
      { id: "ma-2025-05", date: "22.05.2025", topic: "Підсумкова контрольна", status: "PRESENT" },
      { id: "ma-2024-12", date: "18.12.2024", topic: "Лінійні рівняння", status: "EXCUSED" },
    ],
    homework: [
      { id: 1, assignedDate: "17.09.2026", dueDate: "21.09.2026", title: "§ 8, № 124–129", status: "ACTIVE" },
      { id: 2, assignedDate: "14.09.2026", dueDate: "17.09.2026", title: "§ 7, № 108–112", status: "COMPLETED_LATE" },
      { id: 3, assignedDate: "10.09.2026", dueDate: "14.09.2026", title: "Повторити формули скороченого множення", status: "OVERDUE" },
      { id: 4, assignedDate: "07.09.2026", dueDate: "10.09.2026", title: "§ 6, № 91–96", status: "COMPLETED" },
      { id: "mh-2026-05", assignedDate: "20.05.2026", dueDate: "25.05.2026", title: "Підготуватися до підсумкової контрольної", status: "COMPLETED" },
      { id: "mh-2025-12", assignedDate: "10.12.2025", dueDate: "15.12.2025", title: "Повторити теми I семестру", status: "COMPLETED" },
      { id: "mh-2025-05", assignedDate: "18.05.2025", dueDate: "22.05.2025", title: "Завдання для підсумкового повторення", status: "COMPLETED" },
    ],
  },
  {
    id: "ukrainian",
    name: "Українська мова",
    teacher: "Іваненко Наталія Сергіївна",
    previousAverage: 9.1,
    grades: [
      { id: 1, date: "16.09.2026", workType: "Диктант", grade: 10, comment: "" },
      { id: 2, date: "11.09.2026", workType: "Класна робота", grade: 11, comment: "Вдалий письмовий аналіз" },
      { id: 3, date: "09.09.2026", workType: "Домашня робота", grade: 10, comment: "" },
      { id: 4, date: "04.09.2026", workType: "Самостійна робота", grade: 9, comment: "" },
      { id: "u-2026-05", date: "26.05.2026", workType: "Підсумковий диктант", grade: 10, comment: "" },
      { id: "u-2025-12", date: "17.12.2025", workType: "Контрольний твір", grade: 9, comment: "" },
      { id: "u-2025-09", date: "23.09.2025", workType: "Самостійна робота", grade: 10, comment: "" },
      { id: "u-2025-05", date: "21.05.2025", workType: "Підсумкова робота", grade: 9, comment: "" },
      { id: "u-2024-12", date: "16.12.2024", workType: "Диктант", grade: 9, comment: "" },
    ],
    attendance: [
      { id: 1, date: "16.09.2026", topic: "Складнопідрядне речення", status: "PRESENT" },
      { id: 2, date: "11.09.2026", topic: "Засоби зв’язку частин речення", status: "PRESENT" },
      { id: 3, date: "09.09.2026", topic: "Види складних речень", status: "PRESENT" },
      { id: 4, date: "04.09.2026", topic: "Повторення синтаксису", status: "RELEASED" },
    ],
    homework: [
      { id: 1, assignedDate: "16.09.2026", dueDate: "21.09.2026", title: "Вправа 48, скласти схеми речень", status: "ACTIVE" },
      { id: 2, assignedDate: "11.09.2026", dueDate: "16.09.2026", title: "Вправа 42", status: "COMPLETED" },
      { id: 3, assignedDate: "09.09.2026", dueDate: "11.09.2026", title: "Опрацювати § 5", status: "COMPLETED" },
    ],
  },
  {
    id: "geography",
    name: "Географія",
    teacher: "Бойко Віталій Сергійович",
    previousAverage: 8.8,
    grades: [
      { id: 1, date: "15.09.2026", workType: "Практична робота", grade: 8, comment: "Додати умовні позначення" },
      { id: 2, date: "08.09.2026", workType: "Усне опитування", grade: 9, comment: "" },
      { id: 3, date: "01.09.2026", workType: "Класна робота", grade: 8, comment: "" },
      { id: "g-2026-05", date: "19.05.2026", workType: "Підсумкова робота", grade: 9, comment: "" },
      { id: "g-2025-12", date: "16.12.2025", workType: "Практична робота", grade: 8, comment: "" },
      { id: "g-2025-09", date: "22.09.2025", workType: "Усне опитування", grade: 9, comment: "" },
      { id: "g-2025-05", date: "20.05.2025", workType: "Контрольна робота", grade: 8, comment: "" },
      { id: "g-2024-12", date: "12.12.2024", workType: "Практична робота", grade: 8, comment: "" },
    ],
    attendance: [
      { id: 1, date: "15.09.2026", topic: "Топографічні карти", status: "PRESENT" },
      { id: 2, date: "08.09.2026", topic: "Географічні координати", status: "ABSENT" },
      { id: 3, date: "01.09.2026", topic: "Україна на карті Європи", status: "PRESENT" },
    ],
    homework: [
      { id: 1, assignedDate: "15.09.2026", dueDate: "22.09.2026", title: "Нанести об’єкти на контурну карту", status: "ACTIVE" },
      { id: 2, assignedDate: "08.09.2026", dueDate: "15.09.2026", title: "§ 3, запитання 1–5", status: "OVERDUE" },
    ],
  },
  {
    id: "logic",
    name: "Логіка",
    teacher: "Коваленко Олександр Іванович",
    previousAverage: 10,
    grades: [
      { id: 1, date: "18.09.2026", workType: "Практична робота", grade: 11, comment: "" },
      { id: 2, date: "11.09.2026", workType: "Класна робота", grade: 10, comment: "" },
      { id: 3, date: "04.09.2026", workType: "Самостійна робота", grade: 11, comment: "Високий рівень аргументації" },
      { id: "l-2026-05", date: "22.05.2026", workType: "Підсумкова робота", grade: 10, comment: "" },
      { id: "l-2025-12", date: "19.12.2025", workType: "Практична робота", grade: 11, comment: "" },
      { id: "l-2025-09", date: "25.09.2025", workType: "Самостійна робота", grade: 10, comment: "" },
      { id: "l-2025-05", date: "23.05.2025", workType: "Підсумкова робота", grade: 10, comment: "" },
      { id: "l-2024-12", date: "13.12.2024", workType: "Практична робота", grade: 9, comment: "" },
    ],
    attendance: [
      { id: 1, date: "18.09.2026", topic: "Логічні операції", status: "PRESENT" },
      { id: 2, date: "11.09.2026", topic: "Таблиці істинності", status: "PRESENT" },
      { id: 3, date: "04.09.2026", topic: "Поняття висловлювання", status: "PRESENT" },
    ],
    homework: [
      { id: 1, assignedDate: "18.09.2026", dueDate: "25.09.2026", title: "Побудувати таблиці істинності для 5 виразів", status: "ACTIVE" },
      { id: 2, assignedDate: "11.09.2026", dueDate: "18.09.2026", title: "Завдання 7–12", status: "COMPLETED" },
    ],
  },
];

export const academicYears = ["2026/2027", "2025/2026", "2024/2025"];

const monthNames = [
  "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
  "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень",
];

export function getPeriodOptions(academicYear) {
  const startYear = Number(academicYear.split("/")[0]);
  const monthOption = (year, month) => ({
    value: `${year}-${String(month).padStart(2, "0")}`,
    label: `${monthNames[month - 1]} ${year}`,
  });

  return [
    { value: "YEAR", label: "Увесь навчальний рік" },
    { value: "SEMESTER_1", label: "I семестр" },
    ...[9, 10, 11, 12].map((month) => monthOption(startYear, month)),
    { value: "SEMESTER_2", label: "II семестр" },
    ...[1, 2, 3, 4, 5].map((month) => monthOption(startYear + 1, month)),
  ];
}

export function getSubjectPeriodView(subject, academicYear, period) {
  const filterByDate = (items, dateKey) => items.filter((item) => dateMatchesPeriod(item[dateKey], academicYear, period));
  const grades = filterByDate(subject.grades, "date");
  const previous = previousPeriod(academicYear, period);
  const previousGrades = previous
    ? subject.grades.filter((item) => dateMatchesPeriod(item.date, previous.academicYear, previous.period))
    : [];
  const calculatedPreviousAverage = calculateAverage(previousGrades);

  return {
    ...subject,
    grades,
    attendance: filterByDate(subject.attendance, "date"),
    homework: filterByDate(subject.homework, "assignedDate"),
    previousAverage: calculatedPreviousAverage ?? (academicYear === academicYears[0] && period === "2026-09" ? subject.previousAverage : null),
  };
}

export function getGradeForecast(subject, academicYear, period) {
  const yearView = getSubjectPeriodView(subject, academicYear, "YEAR");
  const selectedView = getSubjectPeriodView(subject, academicYear, period);
  const referenceGrades = selectedView.grades.length ? selectedView.grades : yearView.grades;
  const latestGrade = [...referenceGrades].sort((first, second) => dateValue(second.date) - dateValue(first.date))[0];
  const referenceMonth = /^\d{4}-\d{2}$/.test(period)
    ? period
    : latestGrade
      ? monthPeriod(latestGrade.date)
      : null;
  const semesterPeriod = period === "SEMESTER_1" || period === "SEMESTER_2"
    ? period
    : referenceMonth
      ? (Number(referenceMonth.slice(5)) >= 9 ? "SEMESTER_1" : "SEMESTER_2")
      : null;

  const thematicAverage = referenceMonth
    ? calculateAverage(getSubjectPeriodView(subject, academicYear, referenceMonth).grades)
    : null;
  const semesterAverage = semesterPeriod
    ? calculateAverage(getSubjectPeriodView(subject, academicYear, semesterPeriod).grades)
    : null;
  const annualAverage = calculateAverage(yearView.grades);

  return {
    thematic: forecastGrade(thematicAverage),
    semester: forecastGrade(semesterAverage),
    annual: forecastGrade(annualAverage),
  };
}

export function calculateAverage(grades) {
  if (!grades.length) return null;
  const value = grades.reduce((sum, item) => sum + Number(item.grade), 0) / grades.length;
  return Math.round(value * 10) / 10;
}

export function getSubjectStats(subject) {
  const average = calculateAverage(subject.grades);
  const delta = average === null || subject.previousAverage === null || subject.previousAverage === undefined
    ? null
    : Math.round((average - subject.previousAverage) * 10) / 10;
  const missed = subject.attendance.filter((item) => item.status !== "PRESENT").length;
  const missedPercent = subject.attendance.length
    ? Math.round((missed / subject.attendance.length) * 100)
    : 0;
  const activeHomework = subject.homework.filter((item) => item.status === "ACTIVE").length;
  const overdueHomework = subject.homework.filter((item) => item.status === "OVERDUE").length;
  const attended = subject.attendance.filter((item) => item.status === "PRESENT").length;
  const totalLessons = subject.attendance.length;
  const completedHomework = subject.homework.filter((item) => ["COMPLETED", "COMPLETED_LATE"].includes(item.status)).length;
  const completedLateHomework = subject.homework.filter((item) => item.status === "COMPLETED_LATE").length;

  return {
    average,
    delta,
    missed,
    missedPercent,
    attended,
    totalLessons,
    activeHomework,
    overdueHomework,
    completedHomework,
    completedLateHomework,
  };
}

function dateMatchesPeriod(value, academicYear, period) {
  const { year, month } = parseDate(value);
  const startYear = Number(academicYear.split("/")[0]);
  const inAcademicYear = (year === startYear && month >= 9) || (year === startYear + 1 && month <= 8);
  if (!inAcademicYear) return false;
  if (period === "YEAR") return true;
  if (period === "SEMESTER_1") return year === startYear && month >= 9 && month <= 12;
  if (period === "SEMESTER_2") return year === startYear + 1 && month >= 1 && month <= 5;
  return period === `${year}-${String(month).padStart(2, "0")}`;
}

function previousPeriod(academicYear, period) {
  const startYear = Number(academicYear.split("/")[0]);
  if (period === "YEAR") return { academicYear: `${startYear - 1}/${startYear}`, period: "YEAR" };
  if (period === "SEMESTER_1") return { academicYear: `${startYear - 1}/${startYear}`, period: "SEMESTER_2" };
  if (period === "SEMESTER_2") return { academicYear, period: "SEMESTER_1" };

  const [year, month] = period.split("-").map(Number);
  if (month === 9) return { academicYear: `${startYear - 1}/${startYear}`, period: `${year}-05` };
  const previousDate = new Date(year, month - 2, 1);
  return {
    academicYear,
    period: `${previousDate.getFullYear()}-${String(previousDate.getMonth() + 1).padStart(2, "0")}`,
  };
}

function parseDate(value) {
  const [, month, year] = value.split(".").map(Number);
  return { year, month };
}

function monthPeriod(value) {
  const { year, month } = parseDate(value);
  return `${year}-${String(month).padStart(2, "0")}`;
}

function dateValue(value) {
  const [day, month, year] = value.split(".").map(Number);
  return new Date(year, month - 1, day).getTime();
}

function forecastGrade(average) {
  return average === null ? null : average.toLocaleString("uk-UA", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}
