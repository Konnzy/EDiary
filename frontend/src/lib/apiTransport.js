export async function fetchApiResponse(url, options, responseType = "json", timeoutMs = 25000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    let payload = null;
    if (response.status !== 204) {
      if (response.ok && responseType === "blob") payload = await response.blob();
      else {
        try { payload = await response.json(); }
        catch (error) {
          if (controller.signal.aborted) throw error;
          payload = {};
        }
      }
    }
    return { response, payload };
  } catch (error) {
    if (controller.signal.aborted) {
      const timeout = new Error("Server response timed out");
      timeout.name = "TimeoutError";
      throw timeout;
    }
    throw error;
  } finally { clearTimeout(timer); }
}
