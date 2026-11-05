export const IMPORT_PAGE_SIZE = 20;

export function importRows(report, filter) {
  return (report?.rows || []).filter(row => filter !== "errors" || Object.keys(row.errors).length > 0);
}

export function importCellError(row, column, index, columns) {
  if (row.errors[column]) return row.errors[column];
  if (index !== 0) return "";
  return Object.entries(row.errors).filter(([field]) => !columns.includes(field))
    .map(([field, message]) => `${field}: ${message}`).join(" · ");
}
