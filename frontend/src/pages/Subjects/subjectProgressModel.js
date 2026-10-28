const monthNames = [
  "Січень", "Лютий", "Березень", "Квітень", "Травень", "Червень",
  "Липень", "Серпень", "Вересень", "Жовтень", "Листопад", "Грудень",
];

export const PROGRESS_PAGE_SIZE = 20;

export function paginateProgressRows(rows, page, pageSize = PROGRESS_PAGE_SIZE) {
  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const currentPage = Math.min(Math.max(1, page), totalPages);
  const offset = (currentPage - 1) * pageSize;
  return {
    rows: rows.slice(offset, offset + pageSize),
    page: currentPage,
    totalPages,
    total: rows.length,
    start: rows.length ? offset + 1 : 0,
    end: Math.min(offset + pageSize, rows.length),
  };
}

export function sortProgressRows(rows, sort, labels = {}) {
  return [...rows].sort((left, right) => {
    let first = left[sort.column];
    let second = right[sort.column];
    if (sort.column === "status") {
      first = labels[first]?.label || first;
      second = labels[second]?.label || second;
    }
    if (first == null) return second == null ? 0 : 1;
    if (second == null) return -1;
    const comparison = typeof first === "number" && typeof second === "number"
      ? first - second
      : String(first).localeCompare(String(second), "uk", { numeric: true });
    return sort.direction === "desc" ? -comparison : comparison;
  });
}

export function getPeriodOptions(academicYear) {
  if (!academicYear) return [{ value: "YEAR", label: "Увесь навчальний рік" }];
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

export function getCurrentMonthPeriod(academicYear, date = new Date()) {
  if (!academicYear) return "YEAR";
  const calendarMonth = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
  return getPeriodOptions(academicYear).some((item) => item.value === calendarMonth)
    ? calendarMonth
    : "YEAR";
}

export function getSubjectPeriodView(subject, academicYear, period) {
  if (!subject) return null;
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
    previousAverage: calculatedPreviousAverage,
  };
}

export function getGradeForecast(subject, academicYear, period, fractionDigits = 2) {
  if (!subject) return { thematic: null, semester: null, annual: null };
  const yearView = getSubjectPeriodView(subject, academicYear, "YEAR");
  const cutoff = forecastCutoff(academicYear, period);
  const grades = yearView.grades.filter((grade) => grade.date <= cutoff);
  const latestGrade = [...grades].sort(compareGrades).at(-1);
  const referenceMonth = /^\d{4}-\d{2}$/.test(period)
    ? period : latestGrade ? monthPeriod(latestGrade.date) : null;
  const semesterPeriod = period === "SEMESTER_1" || period === "SEMESTER_2"
    ? period
    : referenceMonth
      ? (Number(referenceMonth.slice(5)) >= 9 ? "SEMESTER_1" : "SEMESTER_2")
      : null;

  const thematicAverage = meanScore(currentThematicGrades(grades));
  const semesterAverage = semesterPeriod
    ? semesterForecast(grades, academicYear, semesterPeriod, cutoff)
    : null;
  const recordedSemesterGrades = grades.filter((grade) => gradeType(grade) === "SEMESTER");
  const annualAverage = meanScore([
    ...["SEMESTER_1", "SEMESTER_2"].map((semester) => {
      const recorded = recordedSemesterGrades.filter((grade) => {
        const month = monthPeriod(grade.date);
        return semester === "SEMESTER_1" ? Number(month.slice(5)) >= 9 : Number(month.slice(5)) <= 5;
      });
      return recorded.length ? meanScore(recorded) : semesterForecast(grades, academicYear, semester, cutoff);
    }).filter((value) => value !== null),
  ]);

  return {
    thematic: forecastGrade(thematicAverage, fractionDigits),
    semester: forecastGrade(semesterAverage, fractionDigits),
    annual: forecastGrade(annualAverage, fractionDigits),
  };
}

function forecastCutoff(academicYear, period) {
  const startYear = Number(academicYear.split("/")[0]);
  if (/^\d{4}-\d{2}$/.test(period)) return `${period}-31`;
  if (period === "SEMESTER_1") return `${startYear}-12-31`;
  return `${startYear + 1}-05-31`;
}

function compareGrades(first, second) {
  return first.date.localeCompare(second.date)
    || (first.lessonNumber ?? 0) - (second.lessonNumber ?? 0)
    || (first.id ?? 0) - (second.id ?? 0);
}

function currentThematicGrades(grades) {
  let current = [];
  for (const grade of [...grades].sort(compareGrades)) {
    const type = gradeType(grade);
    if (type === "THEMATIC") current = [];
    else if (type !== "SEMESTER" && type !== "YEAR") current.push(grade);
  }
  return current;
}

function semesterForecast(grades, academicYear, semester, cutoff) {
  const startYear = Number(academicYear.split("/")[0]);
  const start = semester === "SEMESTER_1" ? `${startYear}-09-01` : `${startYear + 1}-01-01`;
  const end = semester === "SEMESTER_1" ? `${startYear}-12-31` : `${startYear + 1}-05-31`;
  const semesterEnd = cutoff < end ? cutoff : end;
  const throughSemester = grades.filter((grade) => grade.date <= semesterEnd);
  const thematicGrades = throughSemester.filter((grade) => grade.date >= start && gradeType(grade) === "THEMATIC");
  const pendingGrades = currentThematicGrades(throughSemester);
  const pendingAverage = pendingGrades.some((grade) => grade.date >= start)
    ? meanScore(pendingGrades) : null;
  return meanScore([...thematicGrades, ...(pendingAverage === null ? [] : [pendingAverage])]);
}

function gradeType(grade) {
  return grade.workTypeCode ?? grade.workType;
}

function meanScore(values) {
  if (!values.length) return null;
  return values.reduce((sum, value) => sum + Number(typeof value === "number" ? value : value.grade), 0) / values.length;
}

export function calculateAverage(grades) {
  if (!grades.length) return null;
  const value = grades.reduce((sum, item) => sum + Number(item.grade), 0) / grades.length;
  return Math.round(value * 10) / 10;
}

export function getSubjectStats(subject) {
  if (!subject) return null;
  const average = calculateAverage(subject.grades);
  const delta = average === null || subject.previousAverage === null || subject.previousAverage === undefined || subject.previousAverage <= 0
    ? null
    : Math.round(((average - subject.previousAverage) / subject.previousAverage) * 1000) / 10;
  const missed = subject.attendance.filter((item) => ["ABSENT", "EXCUSED_ABSENCE"].includes(item.status)).length;
  const exempted = subject.attendance.filter((item) => item.status === "EXEMPTED").length;
  const totalLessons = subject.attendance.length;
  const countedLessons = totalLessons - exempted;
  const missedPercent = countedLessons
    ? Math.round((missed / countedLessons) * 100)
    : 0;
  const activeHomework = subject.homework.filter((item) => item.status === "ASSIGNED").length;
  const overdueHomework = subject.homework.filter((item) => item.status === "OVERDUE").length;
  const attended = subject.attendance.filter((item) => ["PRESENT", "LATE"].includes(item.status)).length;
  const completedHomework = subject.homework.filter((item) => ["COMPLETED", "SUBMITTED_LATE"].includes(item.status)).length;
  const completedLateHomework = subject.homework.filter((item) => item.status === "SUBMITTED_LATE").length;

  return {
    average,
    delta,
    missed,
    missedPercent,
    attended,
    totalLessons,
    countedLessons,
    exempted,
    activeHomework,
    overdueHomework,
    completedHomework,
    completedLateHomework,
  };
}

function dateMatchesPeriod(value, academicYear, period) {
  if (!value || !academicYear) return false;
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
  const [year, month] = value.split("-").map(Number);
  return { year, month };
}

function monthPeriod(value) {
  const { year, month } = parseDate(value);
  return `${year}-${String(month).padStart(2, "0")}`;
}

function forecastGrade(average, fractionDigits) {
  return average === null ? null : average.toLocaleString("uk-UA", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}
