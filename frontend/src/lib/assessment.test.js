import test from "node:test";
import assert from "node:assert/strict";
import { defaultAssessmentPolicy, gradeDisplay, resultOptions } from "./assessment.js";
import { calculateAverage, getGradeForecast } from "../pages/Subjects/subjectProgressModel.js";

test("assessment choices depend on work type and independent permissions", () => {
  assert.deepEqual(resultOptions(defaultAssessmentPolicy,"YEAR"),[]);
  assert.deepEqual(resultOptions({mode:"PASS_FAIL",allowExempted:false},"SEMESTER"),["PASSED","NOT_PASSED"]);
  assert.deepEqual(resultOptions({mode:"NUMERIC",allowPassed:true,allowExempted:true},"THEMATIC"),["PASSED","EXEMPTED"]);
  assert.deepEqual(resultOptions({mode:"PASS_FAIL",allowExempted:true},"CLASSWORK"),[]);
});
test("non-numeric results are displayed and excluded from averages and forecasts", () => {
  assert.equal(gradeDisplay({result:"PASSED"}),"зар.");
  assert.equal(gradeDisplay({result:"NOT_PASSED"}),"не зар.");
  assert.equal(gradeDisplay({result:"EXEMPTED"}),"зв.");
  assert.equal(calculateAverage([{grade:8},{grade:12},{grade:"зар."},{grade:"не зар."},{grade:null}]),10);
  assert.equal(calculateAverage([{grade:"зв."}]),null);
  assert.deepEqual(getGradeForecast({assessmentPolicy:{mode:"PASS_FAIL"}},"2026/2027","YEAR"),{thematic:null,semester:null,annual:null});
});
