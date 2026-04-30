export const getInitialDateFromQuery = (searchParams) => {
  const yearParam = searchParams.get('year');
  const monthParam = searchParams.get('month');
  const y = Number(yearParam);
  const m = Number(monthParam);
  if (Number.isInteger(y) && Number.isInteger(m) && m >= 1 && m <= 12) {
    return new Date(y, m - 1, 1);
  }
  return new Date();
};
