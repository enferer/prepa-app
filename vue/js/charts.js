/* charts.js — construction des graphiques Chart.js pour la prépa. */

function _cssVar(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

let CHART_COLORS = {};
function _rebuildColors() {
  CHART_COLORS = {
    accent: _cssVar("--accent", "#ff6b35"),
    accentSoft: _cssVar("--accent-soft", "rgba(255,107,53,0.35)"),
    muted: _cssVar("--muted", "#93a1ad"),
    grid: "rgba(147,161,173,0.18)",
    info: _cssVar("--info", "#4aa8ff"),
    ok: _cssVar("--ok", "#35c46b"),
    warn: _cssVar("--warn", "#f4b740"),
    text: _cssVar("--text", "#e8edf1"),
  };
  Chart.defaults.color = CHART_COLORS.muted;
  Chart.defaults.borderColor = CHART_COLORS.grid;
}
_rebuildColors();
Chart.defaults.font.family =
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";

function refreshChartsTheme() {
  _rebuildColors();
  Object.values(_charts).forEach((c) => { try { c.destroy(); } catch (_) {} });
  for (const k in _charts) delete _charts[k];
}

const _charts = {};
function _register(id, chart) {
  if (_charts[id]) _charts[id].destroy();
  _charts[id] = chart;
  return chart;
}

function baseScales(extra = {}) {
  return {
    x: { grid: { color: CHART_COLORS.grid }, ticks: { maxRotation: 0, autoSkip: true } },
    y: { grid: { color: CHART_COLORS.grid }, beginAtZero: true, ...extra },
  };
}

function chartVolume(canvas, labels, prevus, realises) {
  return _register(
    "volume",
    new Chart(canvas, {
      type: "bar",
      data: {
        labels,
        datasets: [
          { label: "Prévu (km)", data: prevus, backgroundColor: CHART_COLORS.accentSoft, borderColor: CHART_COLORS.accent, borderWidth: 1, borderRadius: 4 },
          { label: "Réalisé (km)", data: realises, backgroundColor: CHART_COLORS.accent, borderRadius: 4 },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: "top" } },
        scales: baseScales(),
      },
    })
  );
}

function chartLine(id, canvas, labels, data, label, color, opts = {}) {
  return _register(
    id,
    new Chart(canvas, {
      type: "line",
      data: {
        labels,
        datasets: [
          {
            label,
            data,
            borderColor: color,
            backgroundColor: color,
            tension: 0.3,
            pointRadius: 3,
            pointHoverRadius: 5,
            spanGaps: true,
            fill: false,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false }, tooltip: opts.tooltip || {} },
        scales: opts.scales || baseScales(),
      },
    })
  );
}

function chartAllure(canvas, labels, secKm) {
  return _register(
    "allure",
    new Chart(canvas, {
      type: "line",
      data: {
        labels,
        datasets: [
          {
            label: "Allure moy (min/km)",
            data: secKm,
            borderColor: CHART_COLORS.info,
            backgroundColor: CHART_COLORS.info,
            tension: 0.3,
            pointRadius: 3,
            pointHoverRadius: 5,
            spanGaps: true,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (c) => "Allure " + fmtPace(c.parsed.y) } },
        },
        scales: {
          x: { grid: { color: CHART_COLORS.grid } },
          y: {
            reverse: true,
            grid: { color: CHART_COLORS.grid },
            ticks: { callback: (v) => fmtPace(v) },
          },
        },
      },
    })
  );
}
