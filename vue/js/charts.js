/* charts.js — construction des graphiques Chart.js pour la prépa. */

const CHART_COLORS = {
  accent: "#ff6b35",
  accentSoft: "rgba(255,107,53,0.35)",
  muted: "#93a1ad",
  grid: "rgba(147,161,173,0.12)",
  info: "#4aa8ff",
  ok: "#35c46b",
  warn: "#f4b740",
  text: "#e8edf1",
};

Chart.defaults.color = CHART_COLORS.muted;
Chart.defaults.font.family =
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";
Chart.defaults.borderColor = CHART_COLORS.grid;

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

/* Volume hebdo : prévu vs réalisé (barres groupées). */
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

/* Courbe générique (distance, FC, cadence dans le temps). */
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

/* Allure : axe Y inversé (plus haut = plus rapide) et formaté m:ss. */
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
