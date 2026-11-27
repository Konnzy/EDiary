import { afterEach, expect, test, vi } from "vitest";
import { ApiError, authApi } from "./api.js";

afterEach(()=>{vi.restoreAllMocks();vi.useRealTimers();});

test("API errors retain the server status and request reference",async()=>{
  vi.spyOn(window,"fetch").mockResolvedValue(Response.json({message:"Сесію завершено",code:"AUTH_REQUIRED",requestId:"00000000-0000-0000-0000-000000000001"},{status:401}));
  await expect(authApi.getSession()).rejects.toMatchObject({status:401,code:"AUTH_REQUIRED",message:"Сесію завершено",requestId:"00000000-0000-0000-0000-000000000001"});
});

test("unreachable API is a diagnosed network error",async()=>{
  vi.spyOn(window,"fetch").mockRejectedValue(new TypeError("Network unavailable"));
  await expect(authApi.getSession()).rejects.toMatchObject({code:"NETWORK_ERROR",status:0});
});

test("a stalled API call stops loading after the request deadline",async()=>{
  vi.useFakeTimers();
  vi.spyOn(window,"fetch").mockImplementation((_url,{signal})=>new Promise((_resolve,reject)=>signal.addEventListener("abort",()=>reject(new DOMException("Aborted","AbortError")))));
  const result=authApi.getSession().catch(error=>error);
  await vi.advanceTimersByTimeAsync(25001);
  const error=await result;
  expect(error).toBeInstanceOf(ApiError);
  expect(error.code).toBe("REQUEST_TIMEOUT");
});
