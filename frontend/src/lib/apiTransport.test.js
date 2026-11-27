import { test } from "node:test";
import assert from "node:assert/strict";
import { fetchApiResponse } from "./apiTransport.js";

test("aborts a stalled request and reports timeout", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")));
  });
  try { await assert.rejects(fetchApiResponse("/api/test", {}, "json", 10), { name: "TimeoutError" }); }
  finally { globalThis.fetch = original; }
});

test("preserves successful JSON and HTTP errors for the API layer", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ message: "Доступ обмежено" }), { status: 403 });
  try {
    const {response,payload} = await fetchApiResponse("/api/test", {});
    assert.equal(response.status,403);
    assert.equal(payload.message,"Доступ обмежено");
  } finally { globalThis.fetch = original; }
});

test("handles empty responses, downloads and non-JSON gateway errors", async () => {
  const original=globalThis.fetch;
  try {
    globalThis.fetch=async()=>new Response(null,{status:204});
    assert.equal((await fetchApiResponse("/api/test",{})).payload,null);
    globalThis.fetch=async()=>new Response("download",{status:200});
    assert.equal(await (await fetchApiResponse("/api/export",{},"blob")).payload.text(),"download");
    globalThis.fetch=async()=>new Response("<html>Bad Gateway</html>",{status:502});
    const result=await fetchApiResponse("/api/test",{});
    assert.equal(result.response.status,502); assert.deepEqual(result.payload,{});
  } finally { globalThis.fetch=original; }
});

test("keeps a network failure distinct from a timeout", async () => {
  const original=globalThis.fetch;
  const failure=new TypeError("Network unavailable");
  globalThis.fetch=async()=>{throw failure;};
  try {await assert.rejects(fetchApiResponse("/api/test",{}),error=>error===failure);}
  finally {globalThis.fetch=original;}
});

test("also times out while waiting for the response body", async () => {
  const original=globalThis.fetch;
  globalThis.fetch=async(_url,{signal})=>({status:200,ok:true,json:()=>new Promise((_resolve,reject)=>signal.addEventListener("abort",()=>reject(new DOMException("Aborted","AbortError"))))});
  try {await assert.rejects(fetchApiResponse("/api/test",{},"json",10),{name:"TimeoutError"});}
  finally {globalThis.fetch=original;}
});
