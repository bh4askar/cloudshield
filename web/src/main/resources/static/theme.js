// Runs before paint to prevent a flash of the wrong theme.
(function () {
  const root = document.documentElement;
  const system = window.matchMedia("(prefers-color-scheme: dark)");
  let preference;
  try {
    preference = localStorage.getItem("cloudshield-theme");
  } catch (_) {
    /* Storage may be disabled. */
  }
  let theme =
    preference === "light" || preference === "dark"
      ? preference
      : system.matches
        ? "dark"
        : "light";
  function apply(value) {
    theme = value;
    root.dataset.theme = value;
    document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
      const dark = value === "dark";
      button.setAttribute(
        "aria-label",
        `Switch to ${dark ? "light" : "dark"} mode`,
      );
      button.setAttribute("title", `Switch to ${dark ? "light" : "dark"} mode`);
      button.setAttribute("aria-pressed", String(dark));
      button.innerHTML = dark
        ? '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></svg>'
        : '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20.5 14A8.5 8.5 0 0 1 10 3.5 8.5 8.5 0 1 0 20.5 14Z"/></svg>';
    });
  }
  apply(theme);
  document.addEventListener("DOMContentLoaded", () => apply(theme));
  document.addEventListener("click", (event) => {
    if (!event.target.closest("[data-theme-toggle]")) return;
    preference = theme === "dark" ? "light" : "dark";
    try {
      localStorage.setItem("cloudshield-theme", preference);
    } catch (_) {}
    apply(preference);
  });
  system.addEventListener("change", (event) => {
    if (!preference) apply(event.matches ? "dark" : "light");
  });
})();
