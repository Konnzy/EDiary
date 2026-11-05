import { test } from "node:test";
import assert from "node:assert/strict";
import { importRows, importCellError } from "./userImportModel.js";

test("error filter preserves source row numbers and full-report statistics", () => {
  const report = { total: 3, rows: [{number:2,errors:{}},{number:8,errors:{Email:"Invalid"}},{number:25,errors:{Login:"Duplicate"}}] };
  assert.deepEqual(importRows(report,"errors").map(row=>row.number),[8,25]);
  assert.equal(importRows(report,"all").length,3);
  assert.equal(report.total,3);
});
test("only affected cells receive errors, with a fallback for legacy row errors", () => {
  const columns=["Роль","Логін","Електронна пошта"];
  const row={errors:{Логін:"Логін уже існує"}};
  assert.equal(importCellError(row,"Логін",1,columns),"Логін уже існує");
  assert.equal(importCellError(row,"Роль",0,columns),"");
  assert.equal(importCellError({errors:{Дані:"Недійсний запис"}},"Роль",0,columns),"Дані: Недійсний запис");
  assert.equal(importCellError({errors:{Дані:"Недійсний запис"}},"Логін",1,columns),"");
});
