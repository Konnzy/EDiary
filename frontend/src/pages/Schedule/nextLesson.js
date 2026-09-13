const weekdayValues = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];

export function timeToMinutes(value) {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

export function findNextLesson(schedulesByWeek, currentWeek, now, includeAlternateWeek = false) {
  if (currentWeek !== 1 && currentWeek !== 2) return null;

  const todayIndex = (now.getDay() + 6) % 7;
  const minutesNow = now.getHours() * 60 + now.getMinutes();
  const daysToSearch = includeAlternateWeek ? 14 : 7 - todayIndex;

  for (let dayOffset = 0; dayOffset < daysToSearch; dayOffset += 1) {
    const dayPosition = todayIndex + dayOffset;
    const dayIndex = dayPosition % 7;
    const weekday = weekdayValues[dayIndex];
    if (!weekday) continue;

    const week = Math.floor(dayPosition / 7) % 2 === 0 ? currentWeek : 3 - currentWeek;
    const lesson = schedulesByWeek[week]?.lessons
      ?.filter((item) => item.weekday === weekday && (dayOffset > 0 || timeToMinutes(item.startTime) > minutesNow))
      .sort((left, right) => timeToMinutes(left.startTime) - timeToMinutes(right.startTime))[0];

    if (lesson) return { lesson, week, dayIndex };
  }

  return null;
}
