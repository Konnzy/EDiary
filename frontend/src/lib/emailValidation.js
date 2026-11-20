export function hasEmailFormat(address) {
  const [localPart, domain, extraPart] = address.split("@", 3);
  if (!localPart || !domain || extraPart !== undefined || /\s/.test(address)) {
    return false;
  }

  const separator = domain.indexOf(".", 1);
  return separator > 0 && separator < domain.length - 1;
}
