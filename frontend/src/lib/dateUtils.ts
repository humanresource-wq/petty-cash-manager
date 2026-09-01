/**
 * Shared date utility module for the Petty Cash Manager frontend.
 * Provides timezone-safe date formatting and period calculations.
 *
 * IMPORTANT: Uses local timezone (NOT UTC) to avoid the toISOString() bug
 * where IST dates shift backward by a day when converted to UTC.
 */

/**
 * Formats a Date object to 'YYYY-MM-DD' using LOCAL timezone.
 * This avoids the UTC shift bug caused by toISOString().
 *
 * Example: new Date(2026, 6, 31) in IST → '2026-07-31' (correct)
 *          toISOString() would give '2026-07-30' (wrong)
 */
export function formatLocalDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Calculates proper calendar date boundaries for predefined filter periods.
 *
 * - thisMonth:   1st of current month → today
 * - lastMonth:   1st of previous month → last calendar day of previous month
 * - last3Months: 1st of month 2 months ago → today
 * - last6Months: 1st of month 5 months ago → today
 * - all:         empty strings (no filter)
 */
export function calculateDatesForPeriod(period: string): { start: string; end: string } {
  const today = new Date();

  if (period === 'thisMonth') {
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
    return { start: formatLocalDate(firstDay), end: formatLocalDate(today) };
  }

  if (period === 'lastMonth') {
    const firstDayLastMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
    const lastDayLastMonth = new Date(today.getFullYear(), today.getMonth(), 0);
    return { start: formatLocalDate(firstDayLastMonth), end: formatLocalDate(lastDayLastMonth) };
  }

  if (period === 'last3Months') {
    const firstDay3MonthsAgo = new Date(today.getFullYear(), today.getMonth() - 2, 1);
    return { start: formatLocalDate(firstDay3MonthsAgo), end: formatLocalDate(today) };
  }

  if (period === 'last6Months') {
    const firstDay6MonthsAgo = new Date(today.getFullYear(), today.getMonth() - 5, 1);
    return { start: formatLocalDate(firstDay6MonthsAgo), end: formatLocalDate(today) };
  }

  // 'all' or any unrecognized period — no date filter
  return { start: '', end: '' };
}

/**
 * Returns a human-readable label for the selected filter period.
 * Used by the Dashboard stats cards (Spent/Replenished/Cash in Hand).
 *
 * Examples:
 *   thisMonth  → "August 2026"
 *   lastMonth  → "July 2026"
 *   last3Months → "Jun – Aug 2026"
 *   last6Months → "Mar – Aug 2026"
 *   all        → "All Time"
 *   custom     → "1 Jul – 15 Aug 2026"
 */
export function getPeriodLabel(period: string, startDate: string, endDate: string): string {
  if (period === 'all' || (!startDate && !endDate)) {
    return 'All Time';
  }

  const monthNames = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
  ];
  const shortMonthNames = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];

  if (period === 'thisMonth') {
    const now = new Date();
    return `${monthNames[now.getMonth()]} ${now.getFullYear()}`;
  }

  if (period === 'lastMonth') {
    const lastMonth = new Date();
    lastMonth.setMonth(lastMonth.getMonth() - 1);
    return `${monthNames[lastMonth.getMonth()]} ${lastMonth.getFullYear()}`;
  }

  // For multi-month periods and custom ranges, parse the dates
  if (startDate && endDate) {
    const s = new Date(startDate + 'T00:00:00');
    const e = new Date(endDate + 'T00:00:00');

    // Same month and year — show single month
    if (s.getMonth() === e.getMonth() && s.getFullYear() === e.getFullYear()) {
      return `${monthNames[s.getMonth()]} ${s.getFullYear()}`;
    }

    // Different months — show range
    const startLabel = shortMonthNames[s.getMonth()];
    const endLabel = shortMonthNames[e.getMonth()];
    if (s.getFullYear() === e.getFullYear()) {
      return `${startLabel} – ${endLabel} ${e.getFullYear()}`;
    }
    return `${startLabel} ${s.getFullYear()} – ${endLabel} ${e.getFullYear()}`;
  }

  return 'All Time';
}
