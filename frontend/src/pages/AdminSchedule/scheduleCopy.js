export const COPY_MODES = {
  EMPTY_SLOTS: "EMPTY_SLOTS",
  REPLACE: "REPLACE",
};

export function countLessons(schedule) {
  return Object.values(schedule).reduce((total, lessons) => total + lessons.length, 0);
}

export function countCopiedLessons(source, target, mode) {
  if (mode === COPY_MODES.REPLACE) return countLessons(source);

  return Object.entries(source).reduce((total, [weekday, lessons]) => {
    const occupied = new Set((target[weekday] || []).map((lesson) => lesson.lessonNumber));
    return total + lessons.filter((lesson) => !occupied.has(lesson.lessonNumber)).length;
  }, 0);
}

export function copySchedule(source, target, mode, createId = () => crypto.randomUUID()) {
  return Object.fromEntries(Object.keys(source).map((weekday) => {
    const sourceLessons = source[weekday] || [];
    const targetLessons = target[weekday] || [];

    if (mode === COPY_MODES.REPLACE) {
      return [weekday, cloneLessons(sourceLessons, createId)];
    }

    const occupied = new Set(targetLessons.map((lesson) => lesson.lessonNumber));
    const copied = sourceLessons.filter((lesson) => !occupied.has(lesson.lessonNumber));
    return [
      weekday,
      [...targetLessons, ...cloneLessons(copied, createId)]
        .sort((first, second) => first.lessonNumber - second.lessonNumber),
    ];
  }));
}

function cloneLessons(lessons, createId) {
  return lessons.map((lesson) => ({ ...lesson, id: createId() }));
}
