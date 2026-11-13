async function initializeLogin() {
  const error = document.getElementById("login-error");
  if (new URLSearchParams(location.search).has("error")) {
    error.textContent = "Incorrect username or password. Please try again.";
    error.hidden = false;
  }
  if (new URLSearchParams(location.search).has("google_error")) {
    error.textContent =
      "Google sign-in could not be completed. Please try again.";
    error.hidden = false;
  }
  if (new URLSearchParams(location.search).has("account_error")) {
    error.textContent =
      "Your workspace could not be created. Contact the site operator or try again later.";
    error.hidden = false;
  }
  try {
    const response = await fetch("/api/csrf");
    if (!response.ok) throw new Error();
    const csrf = await response.json();
    const field = document.getElementById("csrf-field");
    field.name = csrf.parameterName;
    field.value = csrf.token;
    document.getElementById("sign-in").disabled = false;
    const mode = await (await fetch("/public/mode")).json();
    document.getElementById("demo-credentials").hidden = !mode.demo;
    const google = document.getElementById("google-login");
    if (mode.googleEnabled) {
      google.href = "/oauth2/authorization/google";
      google.removeAttribute("aria-disabled");
      google.removeAttribute("tabindex");
    } else {
      document.getElementById("google-status").textContent =
        "Google sign-in is not connected yet. Administrator access below opens the local demo only.";
    }
  } catch (e) {
    error.textContent =
      "Cannot connect to CloudShield. Restart the app and reload this page.";
    error.hidden = false;
  }
}
initializeLogin();
