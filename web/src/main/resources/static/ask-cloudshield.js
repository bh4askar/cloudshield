(() => {
  const widget = document.getElementById("ask-cloudshield");
  if (!widget) return;
  const get = (id) => document.getElementById(id);
  const panel = get("ask-panel"),
    launcher = get("ask-launcher"),
    input = get("ask-input");
  const log = get("ask-messages"),
    chips = get("ask-suggestions"),
    send = get("ask-send");
  let timer = null,
    previous = null,
    busy = false;
  const starters = [
    "How do I create a backup?",
    "Why is my readiness score low?",
    "How do I restore data?",
  ];
  const scroll = () => {
    log.scrollTop = log.scrollHeight;
  };
  function message(text, user = false, links = []) {
    const row = document.createElement("div");
    row.className = "ask-message" + (user ? " ask-message-user" : "");
    const label = document.createElement("span");
    label.className = "ask-message-label";
    label.textContent = user ? "You" : "Ask CloudShield";
    const bubble = document.createElement("div");
    bubble.className = "ask-bubble";
    const body = document.createElement("p");
    body.textContent = text;
    bubble.append(body);
    if (links.length) {
      const actions = document.createElement("div");
      actions.className = "ask-answer-links";
      links.forEach((link) => {
        const anchor = document.createElement("a");
        anchor.href = link.href;
        anchor.textContent = link.label;
        actions.append(anchor);
      });
      bubble.append(actions);
    }
    row.append(label, bubble);
    log.append(row);
    while (log.children.length > 40) log.firstElementChild.remove();
    scroll();
    return row;
  }
  function suggestions(questions) {
    chips.replaceChildren();
    questions.forEach((question) => {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = question;
      button.disabled = busy;
      button.addEventListener("click", () => ask(question));
      chips.append(button);
    });
  }
  function updateSend() {
    send.disabled = busy || !input.value.trim();
  }
  function reset() {
    clearTimeout(timer);
    timer = null;
    busy = false;
    previous = null;
    log.replaceChildren();
    input.value = "";
    message(
      "Hi, I’m Ask CloudShield 👋\nHow can I help you today? Ask about a backup, a recovery problem, or your workspace status.",
    );
    suggestions(starters);
    updateSend();
  }
  function expand(open) {
    panel.hidden = !open;
    launcher.setAttribute("aria-expanded", String(open));
    if (open) {
      input.focus({ preventScroll: true });
      scroll();
    } else launcher.focus({ preventScroll: true });
  }
  function ask(value) {
    const question = String(value).trim().slice(0, 500);
    if (!question || busy) return;
    message(question, true);
    input.value = "";
    busy = true;
    updateSend();
    chips.querySelectorAll("button").forEach((b) => {
      b.disabled = true;
    });
    const typing = document.createElement("div");
    typing.className = "ask-message ask-typing";
    typing.setAttribute("aria-label", "Ask CloudShield is preparing a reply");
    const dots = document.createElement("div");
    dots.className = "ask-bubble";
    for (let n = 0; n < 3; n++) {
      const dot = document.createElement("i");
      dot.setAttribute("aria-hidden", "true");
      dots.append(dot);
    }
    typing.append(dots);
    log.append(typing);
    scroll();
    timer = setTimeout(() => {
      const data = typeof state !== "undefined" ? state : null;
      const score =
        data && typeof readiness === "function" ? readiness(data) : null;
      const reply = CloudShieldFAQ.answer(
        question,
        { data, readiness: score },
        previous,
      );
      typing.remove();
      message(reply.text, false, reply.links);
      previous = reply.id === "unknown" ? null : reply.id;
      busy = false;
      timer = null;
      suggestions(reply.suggestions);
      updateSend();
    }, 650);
  }
  launcher.addEventListener("click", () => expand(panel.hidden));
  get("ask-close").addEventListener("click", () => expand(false));
  get("ask-clear").addEventListener("click", () => {
    reset();
    input.focus({ preventScroll: true });
  });
  get("ask-form").addEventListener("submit", (event) => {
    event.preventDefault();
    ask(input.value);
  });
  input.addEventListener("input", updateSend);
  widget.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !panel.hidden) {
      event.preventDefault();
      event.stopPropagation();
      expand(false);
    }
  });
  reset();
})();
