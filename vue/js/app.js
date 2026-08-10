/* app.js — rendu multi-profils / multi-prépas. */

const CATALOG = window.PREPA_DATA || { profils: [] };
const LS_KEY = "prepa.selection";
const LS_THEME = "prepa.theme";

let CURRENT = { profil: null, prepa: null };
let OBJ = {};
let PLAN = { semaines: [] };
let ACTS = [];
let JOURNAL = [];
let VIEW_WEEK_IDX = null;

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */
const MOIS = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."];
const JOURS = ["dim.", "lun.", "mar.", "mer.", "jeu.", "ven.", "sam."];
const JOURS_LONGS = ["dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"];

function toISODate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function parseISO(s) {
  if (!s) return null;
  const d = new Date(s + (s.length <= 10 ? "T00:00:00" : ""));
  return isNaN(d) ? null : d;
}
function fmtDate(s, withDow = false) {
  const d = parseISO(s);
  if (!d) return "—";
  const base = `${d.getDate()} ${MOIS[d.getMonth()]}`;
  return withDow ? `${JOURS[d.getDay()]} ${base}` : base;
}
function fmtPace(secKm) {
  if (secKm == null || isNaN(secKm)) return "—";
  const m = Math.floor(secKm / 60);
  const s = Math.round(secKm % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}
function fmtDur(sec) {
  if (sec == null || isNaN(sec)) return "—";
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = Math.round(sec % 60);
  return h > 0 ? `${h}h${String(m).padStart(2, "0")}` : `${m}:${String(s).padStart(2, "0")}`;
}
function km(v, dec = 1) {
  return v == null ? "—" : v.toFixed(dec).replace(".", ",");
}
function todayMidnight() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}
function daysBetween(a, b) {
  return Math.round((b - a) / 86400000);
}
function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}
function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

const ACRONYMES = ["RP", "VMA", "FC", "AM", "EF", "SL", "PPS", "PPG"];
function humanize(key) {
  let s = String(key)
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/([a-zA-Z])(\d)/g, "$1 $2")
    .replace(/(\d)([a-zA-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .toLowerCase();
  s = s.charAt(0).toUpperCase() + s.slice(1);
  ACRONYMES.forEach((a) => {
    s = s.replace(new RegExp(`\\b${a}\\b`, "gi"), a);
  });
  return s;
}

function volumeRealise(dateDebut) {
  const start = parseISO(dateDebut);
  if (!start) return 0;
  const end = new Date(start);
  end.setDate(end.getDate() + 7);
  return ACTS.reduce((tot, a) => {
    const d = parseISO(a.date);
    if (d && d >= start && d < end && a.distanceKm) return tot + a.distanceKm;
    return tot;
  }, 0);
}
function sommeKm(filtre) {
  return ACTS.reduce((t, a) => (a.distanceKm && filtre(a) ? t + a.distanceKm : t), 0);
}
function toutesSeances() {
  const out = [];
  (PLAN.semaines || []).forEach((s) =>
    (s.seances || []).forEach((se) => out.push({ ...se, semaine: s.numero }))
  );
  return out;
}
function typeIcon(type) {
  if (type === "Renfo") return "💪";
  if (type === "Côtes" || type === "Cotes") return "⛰️";
  return "🏃";
}

const STATUT_FAIT = ["validee", "adaptee", "modifiee"];
const byDate = (a, b) => (a.date || "").localeCompare(b.date || "");

function semaineCourante() {
  const today = todayMidnight();
  const sems = PLAN.semaines || [];
  for (const s of sems) {
    const start = parseISO(s.dateDebut);
    if (!start) continue;
    const end = new Date(start);
    end.setDate(end.getDate() + 7);
    if (today >= start && today < end) return s;
  }
  const futures = sems.filter((s) => parseISO(s.dateDebut) && parseISO(s.dateDebut) >= today);
  if (futures.length) return futures[0];
  return sems[sems.length - 1] || null;
}

/* ------------------------------------------------------------------ */
/* En-tête                                                             */
/* ------------------------------------------------------------------ */
function renderHeader() {
  const c = OBJ.course || {};
  const profilNom = CURRENT.profil ? CURRENT.profil.nom : "";
  const prepaNom = c.nom || (CURRENT.prepa ? CURRENT.prepa.nom : "Prépa");
  const nomEl = document.getElementById("course-nom");
  nomEl.textContent = "";
  if (profilNom) {
    nomEl.append(document.createTextNode(profilNom));
    const sep = document.createElement("span");
    sep.className = "brand-prepa";
    sep.textContent = ` · ${prepaNom}`;
    nomEl.appendChild(sep);
  } else {
    nomEl.textContent = prepaNom;
  }
  const parts = [];
  if (c.date) parts.push("Course le " + fmtDate(c.date, true));
  if (OBJ.chronoVise) parts.push("Objectif " + OBJ.chronoVise);
  document.getElementById("course-sous").textContent = parts.join(" · ");
  if (CATALOG.genereLe) {
    document.getElementById("genere-le").textContent =
      "Généré le " + fmtDate(CATALOG.genereLe.slice(0, 10), true);
  }
}

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */
function renderDashboard() {
  const root = document.getElementById("tab-dashboard");
  if (!OBJ.course) {
    root.innerHTML = `<div class="empty">Aucune prépa initialisée pour ce profil.<br>Lance le skill <b>/prepa-init</b>.</div>`;
    return;
  }
  const c = OBJ.course;
  const today = todayMidnight();
  const raceDate = parseISO(c.date);
  const jours = raceDate ? Math.max(0, daysBetween(today, raceDate)) : null;
  const semaines = jours != null ? Math.floor(jours / 7) : null;

  root.innerHTML = "";

  const am = OBJ.alluresCibles && OBJ.alluresCibles.AM ? OBJ.alluresCibles.AM.affichage : null;
  root.appendChild(el(`
    <div class="hero card">
      <div class="hero-main">
        <div class="hero-count"><span class="num">${jours != null ? jours : "—"}</span><span class="unit">jours avant la course</span></div>
        <div class="hero-sub muted">${escapeHtml(c.nom || "")}${c.date ? " · " + fmtDate(c.date, true) : ""}${semaines != null ? " · ~" + semaines + " sem." : ""}</div>
      </div>
      <div class="hero-obj">
        <div class="hero-obj-val">${OBJ.chronoVise || "—"}</div>
        <div class="muted small">Objectif${am ? " · allure marathon " + am + "/km" : ""}</div>
      </div>
    </div>`));

  const s = toutesSeances();
  const faites = s.filter((x) => STATUT_FAIT.includes(x.statut));
  const manquees = s.filter((x) => x.statut === "manquee");
  const restantes = s.filter((x) => x.statut === "a_venir");
  const resolu = faites.length + manquees.length;
  const assidu = resolu ? Math.round((faites.length / resolu) * 100) : null;

  const debut = OBJ.dateDebutPrepa;
  const kmDurant = debut ? sommeKm((a) => a.date && a.date >= debut) : sommeKm(() => true);
  const kmCibleTotal = (PLAN.semaines || []).reduce((t, w) => t + (w.volumeCibleKm || 0), 0);

  const kpis = [
    { icon: "✅", val: faites.length, label: "Séances faites" },
    { icon: "🗓️", val: restantes.length, label: "Séances restantes" },
    { icon: "⛔", val: manquees.length, label: "Séances loupées", danger: manquees.length > 0 },
    { icon: "🎯", val: assidu != null ? assidu + "%" : "—", label: "Assiduité" },
    { icon: "🏃", val: km(kmDurant, 0), unit: "km", label: "Km depuis le début", sub: kmCibleTotal ? "cible " + km(kmCibleTotal, 0) + " km" : "" },
    { icon: "📊", val: km(kmCibleTotal ? (kmDurant / kmCibleTotal) * 100 : 0, 0), unit: "%", label: "Volume vs cible" },
  ];
  const kgrid = el(`<div class="kpis"></div>`);
  kpis.forEach((k) => kgrid.appendChild(el(`
    <div class="kpi ${k.danger ? "kpi-danger" : ""}">
      <div class="kpi-icon">${k.icon}</div>
      <div class="kpi-val">${k.val}${k.unit ? `<span class="kpi-unit">${k.unit}</span>` : ""}</div>
      <div class="kpi-label">${k.label}</div>
      ${k.sub ? `<div class="kpi-sub">${k.sub}</div>` : ""}
    </div>`)));
  root.appendChild(kgrid);

  const total = s.length;
  const pctAv = total ? Math.round((resolu / total) * 100) : 0;
  root.appendChild(el(`
    <div class="card" style="margin-top:16px">
      <div style="display:flex;justify-content:space-between;font-size:.9rem"><span>Avancement de la prépa</span><span class="muted">${resolu}/${total} séances passées · ${pctAv}%</span></div>
      <div class="bar" style="margin-top:8px"><span style="width:${pctAv}%"></span></div>
    </div>`));

  const weekHolder = el(`<div id="week-nav-holder" style="margin-top:20px"></div>`);
  root.appendChild(weekHolder);
  if (VIEW_WEEK_IDX == null) VIEW_WEEK_IDX = indexSemaineCourante();
  renderWeekView();

  root.appendChild(el(`<h2 class="section-title">Volume hebdomadaire — prévu vs réalisé</h2>`));
  root.appendChild(el(`<div class="card"><div class="chart-box"><canvas id="cv-volume"></canvas></div></div>`));
  const sems = PLAN.semaines || [];
  chartVolume(
    document.getElementById("cv-volume"),
    sems.map((w) => "S" + w.numero),
    sems.map((w) => w.volumeCibleKm || 0),
    sems.map((w) => Math.round(volumeRealise(w.dateDebut) * 10) / 10)
  );
}

function indexSemaineCourante() {
  const today = todayMidnight();
  const sems = PLAN.semaines || [];
  for (let i = 0; i < sems.length; i++) {
    const start = parseISO(sems[i].dateDebut);
    if (!start) continue;
    const end = new Date(start);
    end.setDate(end.getDate() + 7);
    if (today >= start && today < end) return i;
  }
  const idx = sems.findIndex((s) => parseISO(s.dateDebut) && parseISO(s.dateDebut) >= today);
  if (idx >= 0) return idx;
  return Math.max(0, sems.length - 1);
}

function relLabel(offset) {
  if (offset === 0) return "Cette semaine";
  if (offset === -1) return "Semaine passée";
  if (offset === 1) return "Semaine prochaine";
  if (offset < 0) return `Il y a ${-offset} sem.`;
  return `Dans ${offset} sem.`;
}

function renderWeekView() {
  const holder = document.getElementById("week-nav-holder");
  if (!holder) return;
  holder.innerHTML = "";
  const sems = PLAN.semaines || [];
  if (!sems.length) {
    holder.appendChild(el(`<div class="card muted">Aucune semaine planifiée.</div>`));
    return;
  }
  const currentIdx = indexSemaineCourante();
  VIEW_WEEK_IDX = Math.max(0, Math.min(sems.length - 1, VIEW_WEEK_IDX ?? currentIdx));
  const idx = VIEW_WEEK_IDX;
  const sem = sems[idx];
  const start = parseISO(sem.dateDebut);
  const today = todayMidnight();
  const seances = (sem.seances || []).slice().sort(byDate);
  const real = volumeRealise(sem.dateDebut);
  const done = seances.filter((x) => STATUT_FAIT.includes(x.statut)).length;
  const cible = sem.volumeCibleKm || 0;
  const offset = idx - currentIdx;
  const isCurrent = offset === 0;

  const fin = new Date(start);
  fin.setDate(fin.getDate() + 6);
  const dates = `${fmtDate(sem.dateDebut)} → ${fmtDate(toISODate(fin))}`;

  const nav = el(`
    <div class="week-nav">
      <button class="wn-btn" id="wn-prev" ${idx === 0 ? "disabled" : ""} aria-label="Semaine précédente" title="Semaine précédente">
        <span class="wn-arrow">‹</span>
        <span class="wn-btn-label">Préc.</span>
      </button>
      <div class="wn-center">
        <div class="wn-title">
          <span>${relLabel(offset)}</span>
          <span class="wk-now">S${sem.numero}${sem.bloc ? " · " + escapeHtml(sem.bloc) : ""}</span>
        </div>
        <div class="wn-dates">${dates}</div>
      </div>
      <button class="wn-btn" id="wn-next" ${idx === sems.length - 1 ? "disabled" : ""} aria-label="Semaine suivante" title="Semaine suivante">
        <span class="wn-arrow">›</span>
        <span class="wn-btn-label">Suiv.</span>
      </button>
    </div>`);
  holder.appendChild(nav);
  if (!isCurrent) {
    holder.appendChild(el(`<div class="wn-actions"><button class="wn-today" id="wn-today" type="button"><span class="wn-today-icon">📍</span> Revenir à cette semaine</button></div>`));
  }

  const meta = el(`
    <div class="week-meta-row">
      <span class="wm-chip">🏃 ${done}/${seances.length} séance${seances.length > 1 ? "s" : ""}</span>
      <span class="wm-chip">📏 ${km(real)}${cible ? " / " + km(cible, 0) : ""} km</span>
    </div>`);
  holder.appendChild(meta);

  const strip = el(`<div class="week-strip"></div>`);
  for (let i = 0; i < 7; i++) {
    const d = new Date(start);
    d.setDate(d.getDate() + i);
    const iso = toISODate(d);
    const dayS = seances.filter((se) => se.date === iso);
    const isToday = isCurrent && d.getTime() === today.getTime();
    const isPast = d.getTime() < today.getTime();
    strip.appendChild(renderDayRow(d, dayS, { isToday, isPast }));
  }
  holder.appendChild(strip);

  if (sem.note) holder.appendChild(el(`<div class="week-note">📝 ${escapeHtml(sem.note)}</div>`));

  document.getElementById("wn-prev").addEventListener("click", () => { VIEW_WEEK_IDX = idx - 1; renderWeekView(); });
  document.getElementById("wn-next").addEventListener("click", () => { VIEW_WEEK_IDX = idx + 1; renderWeekView(); });
  const tbtn = document.getElementById("wn-today");
  if (tbtn) tbtn.addEventListener("click", () => { VIEW_WEEK_IDX = currentIdx; renderWeekView(); });
}

function renderDayRow(d, seances, { isToday, isPast }) {
  const cls = ["day-row"];
  if (isToday) cls.push("today");
  else if (isPast) cls.push("past");
  if (!seances.length) cls.push("rest");

  const dow = JOURS[d.getDay()];
  const num = d.getDate();

  const row = el(`<div class="${cls.join(" ")}"></div>`);
  row.appendChild(el(`
    <div class="day-cell">
      <span>${dow}</span>
      <span class="day-num">${num}</span>
    </div>`));

  const content = el(`<div class="day-content"></div>`);
  if (!seances.length) {
    content.appendChild(el(`<div class="drest">Repos</div>`));
  } else {
    seances.forEach((se) => {
      const statut = se.statut || "a_venir";
      const label = { validee: "Validée", a_venir: "À venir", adaptee: "Adaptée", modifiee: "Modifiée", manquee: "Manquée" }[statut] || statut;
      const meta = [];
      if (se.type) meta.push(escapeHtml(se.type));
      if (se.distanceCibleKm) meta.push(km(se.distanceCibleKm) + " km");
      else if (se.dureeCibleMin) meta.push(se.dureeCibleMin + " min");
      if (se.alluresCibles) meta.push("🎯 " + escapeHtml(se.alluresCibles));

      const details = [];
      if (se.description) details.push(`<div class="sdesc">${escapeHtml(se.description)}</div>`);
      if (se.focus && se.type === "Renfo") details.push(`<div class="sallure">🏋️ ${escapeHtml(se.focus)}</div>`);
      if (se.commentaireCoach) details.push(`<div class="scoach">💬 ${escapeHtml(se.commentaireCoach)}</div>`);

      const head = `
        <div class="dtitle">
          <span class="icn">${typeIcon(se.type)}</span>
          <span>${escapeHtml(se.titre || "Séance")}</span>
          <span class="badge ${statut}">${label}</span>
        </div>
        <div class="dmeta">${meta.join(" · ")}</div>`;

      if (details.length) {
        content.appendChild(el(`
          <details class="dseance">
            <summary>
              <div class="dseance-body">${head}</div>
              <span class="dchev" aria-hidden="true">▾</span>
            </summary>
            <div class="dexpand">${details.join("")}</div>
          </details>`));
      } else {
        content.appendChild(el(`<div class="dseance dseance-plain">${head}</div>`));
      }
    });
  }
  row.appendChild(content);
  return row;
}

function renderSeance(s) {
  const statut = s.statut || "a_venir";
  const label = { validee: "Validée", a_venir: "À venir", adaptee: "Adaptée", modifiee: "Modifiée", manquee: "Manquée" }[statut] || statut;
  const renfo = s.type === "Renfo";
  const meta = s.distanceCibleKm ? " · " + km(s.distanceCibleKm) + " km" : s.dureeCibleMin ? " · " + s.dureeCibleMin + " min" : "";
  return el(`
    <div class="seance ${renfo ? "renfo" : "course"}">
      <div class="stype"><span class="ic">${typeIcon(s.type)}</span><span>${escapeHtml(s.type || "?")}</span></div>
      <div class="sbody">
        <div class="stitle">${escapeHtml(s.titre || "Séance")} <span class="badge ${statut}">${label}</span></div>
        <div class="sdate">${fmtDate(s.date, true)}${meta}</div>
        ${s.description ? `<div class="sdesc">${escapeHtml(s.description)}</div>` : ""}
        ${s.alluresCibles ? `<div class="sallure">🎯 ${escapeHtml(s.alluresCibles)}</div>` : s.focus ? `<div class="sallure">🏋️ ${escapeHtml(s.focus)}</div>` : ""}
        ${s.commentaireCoach ? `<div class="scoach">💬 ${escapeHtml(s.commentaireCoach)}</div>` : ""}
      </div>
    </div>`);
}

/* ------------------------------------------------------------------ */
/* Stats — comparaison avant / pendant prépa, best efforts, tendances  */
/* ------------------------------------------------------------------ */

/* Classification : allure moyenne pertinente (continu) vs non (fractionné). */
const TYPE_CONTINU = new Set(["EF", "SL", "AM", "SL AM"]);
const TYPE_INTERVAL = new Set(["VMA", "Seuil", "Côtes", "Cotes"]);
function normType(t) {
  if (!t) return null;
  if (t === "Cotes") return "Côtes";
  return t;
}
function isCourse(t) {
  const n = normType(t);
  return n && n !== "Renfo";
}
function typeContinu(t) { return TYPE_CONTINU.has(normType(t)); }
function typeInterval(t) { return TYPE_INTERVAL.has(normType(t)); }

/* Joint activités ↔ séances planifiées par date.
   Renvoie une copie annotée avec planType + plannedSeance.
   Si plusieurs séances course le même jour, on apparie par proximité de distance. */
function joinActivitiesToPlan() {
  const plannedByDate = {};
  (PLAN.semaines || []).forEach((sem) =>
    (sem.seances || []).forEach((se) => {
      if (!isCourse(se.type) || !se.date) return;
      (plannedByDate[se.date] = plannedByDate[se.date] || []).push({ ...se, semaine: sem.numero });
    })
  );
  const used = new Set();
  const key = (p) => `${p.date}::${p.titre}::${p.type}`;

  const courseActs = ACTS.filter((a) => (a.type || "").toLowerCase().includes("course"));
  return courseActs.map((a) => {
    const day = (plannedByDate[a.date] || []).filter((p) => !used.has(key(p)));
    if (!day.length) return { ...a, planType: null, planned: null };
    let best = day[0], bestDiff = Infinity;
    day.forEach((p) => {
      const d = Math.abs((p.distanceCibleKm || 0) - (a.distanceKm || 0));
      if (d < bestDiff) { bestDiff = d; best = p; }
    });
    used.add(key(best));
    return { ...a, planType: normType(best.type), planned: best };
  });
}

function agrege(acts) {
  const km = acts.reduce((t, a) => t + (a.distanceKm || 0), 0);
  const durs = acts.reduce((t, a) => t + (a.dureeSec || 0), 0);
  const nbSem = acts.length ? Math.max(1, nbSemainesCouvertes(acts)) : 0;
  return {
    nb: acts.length,
    km,
    durs,
    kmParSemaine: nbSem ? km / nbSem : 0,
    seancesParSemaine: nbSem ? acts.length / nbSem : 0,
    dureeMoy: acts.length ? durs / acts.length : 0,
    nbSem,
  };
}

function statsParType(acts) {
  const groups = {};
  acts.forEach((a) => {
    const k = a.planType || "Hors plan";
    (groups[k] = groups[k] || []).push(a);
  });
  const out = [];
  Object.entries(groups).forEach(([type, list]) => {
    const nb = list.length;
    const km = list.reduce((t, a) => t + (a.distanceKm || 0), 0);
    const durs = list.reduce((t, a) => t + (a.dureeSec || 0), 0);
    const paces = list.filter((a) => a.allureMoySecKm).map((a) => a.allureMoySecKm);
    const fcs = list.filter((a) => a.fcMoy).map((a) => a.fcMoy);
    const paceMoy = typeContinu(type) && paces.length ? paces.reduce((a, b) => a + b, 0) / paces.length : null;
    const fcMoy = fcs.length ? Math.round(fcs.reduce((a, b) => a + b, 0) / fcs.length) : null;
    const cible = OBJ.alluresCibles && OBJ.alluresCibles[type] ? OBJ.alluresCibles[type].secKm : null;
    out.push({ type, nb, km, durs, paceMoy, cibleSec: cible, fcMoy });
  });
  const rank = ["EF", "SL", "AM", "Seuil", "VMA", "Côtes", "Hors plan"];
  out.sort((a, b) => (rank.indexOf(a.type) + 100) - (rank.indexOf(b.type) + 100));
  return out;
}

function nbSemainesCouvertes(acts) {
  if (!acts.length) return 0;
  const dates = acts.map((a) => parseISO(a.date)).filter(Boolean).sort((a, b) => a - b);
  if (!dates.length) return 0;
  const days = Math.round((dates[dates.length - 1] - dates[0]) / 86400000) + 1;
  return Math.max(1, days / 7);
}

function delta(now, prev, opts = {}) {
  if (now == null || prev == null || prev === 0) return "";
  const diff = now - prev;
  const pct = (diff / prev) * 100;
  const better = opts.lowerIsBetter ? diff < 0 : diff > 0;
  const cls = Math.abs(pct) < 1 ? "" : better ? "up" : "down";
  const sign = diff > 0 ? "+" : "";
  const val = opts.pace ? (sign + (Math.round(diff) + "s/km")) : (sign + Math.round(pct * 10) / 10 + "%");
  return `<div class="ct-delta ${cls}">${val}</div>`;
}

function compareTile(label, prevStr, nowStr, deltaHtml) {
  return `
    <div class="compare-tile">
      <div class="ct-label">${label}</div>
      <div class="ct-rows">
        <div class="ct-col"><div class="ct-side">Avant prépa</div><div class="ct-val">${prevStr}</div></div>
        <div class="ct-col now"><div class="ct-side">Pendant prépa</div><div class="ct-val">${nowStr}</div></div>
      </div>
      ${deltaHtml}
    </div>`;
}

/* Meilleur effort courant : sous-fenêtre continue de distance ≥ D
   approximée sur une activité (allure moyenne × D). */
function bestEffort(distMin) {
  let best = null;
  ACTS.forEach((a) => {
    if (!a.distanceKm || !a.allureMoySecKm || a.distanceKm < distMin) return;
    const t = distMin * a.allureMoySecKm;
    if (!best || t < best.tempsSec) {
      best = { tempsSec: t, paceSec: a.allureMoySecKm, date: a.date, titre: a.titre || a.type };
    }
  });
  return best;
}

function renderStats() {
  const root = document.getElementById("tab-stats");
  if (!ACTS.length) {
    root.innerHTML = `<div class="empty">Aucune activité.<br>Colle tes séances Garmin dans <b>data/garmin.csv</b> puis lance <code>python3 scripts/build_data.py</code>.</div>`;
    return;
  }
  root.innerHTML = "";

  const debut = OBJ.dateDebutPrepa;
  const joined = joinActivitiesToPlan();
  const avant = debut ? joined.filter((a) => a.date && a.date < debut) : [];
  const pendant = debut ? joined.filter((a) => a.date && a.date >= debut) : joined.slice();
  const aPrev = agrege(avant);
  const aNow = agrege(pendant);

  /* ---------- Compare avant / pendant (volume seulement) ---------- */
  root.appendChild(el(`<h2 class="section-title">Avant vs pendant la prépa <span class="sub">${aPrev.nb} → ${aNow.nb} séances</span></h2>`));
  const cg = el(`<div class="compare-grid"></div>`);
  cg.appendChild(el(compareTile(
    "Volume hebdo",
    km(aPrev.kmParSemaine, 1) + " km",
    km(aNow.kmParSemaine, 1) + " km",
    delta(aNow.kmParSemaine, aPrev.kmParSemaine)
  )));
  cg.appendChild(el(compareTile(
    "Séances / semaine",
    aPrev.seancesParSemaine ? aPrev.seancesParSemaine.toFixed(1) : "—",
    aNow.seancesParSemaine ? aNow.seancesParSemaine.toFixed(1) : "—",
    delta(aNow.seancesParSemaine, aPrev.seancesParSemaine)
  )));
  cg.appendChild(el(compareTile(
    "Distance moyenne",
    aPrev.nb ? km(aPrev.km / aPrev.nb) + " km" : "—",
    aNow.nb ? km(aNow.km / aNow.nb) + " km" : "—",
    delta(aNow.nb ? aNow.km / aNow.nb : null, aPrev.nb ? aPrev.km / aPrev.nb : null)
  )));
  cg.appendChild(el(compareTile(
    "Durée moyenne",
    aPrev.dureeMoy ? fmtDur(aPrev.dureeMoy) : "—",
    aNow.dureeMoy ? fmtDur(aNow.dureeMoy) : "—",
    delta(aNow.dureeMoy, aPrev.dureeMoy)
  )));
  cg.appendChild(el(compareTile(
    "Volume total",
    km(aPrev.km, 0) + " km",
    km(aNow.km, 0) + " km",
    ""
  )));
  cg.appendChild(el(compareTile(
    "Semaines couvertes",
    aPrev.nbSem ? Math.round(aPrev.nbSem) + " sem." : "—",
    aNow.nbSem ? Math.round(aNow.nbSem) + " sem." : "—",
    ""
  )));
  root.appendChild(cg);

  /* ---------- Par type de séance (pendant la prépa) ---------- */
  const parType = statsParType(pendant);
  root.appendChild(el(`<h2 class="section-title">Par type de séance <span class="sub">liaison activité ↔ plan par date · pendant la prépa</span></h2>`));
  if (!parType.length) {
    root.appendChild(el(`<div class="card muted small">Aucune séance depuis le début de la prépa.</div>`));
  } else {
    const grid = el(`<div class="type-grid"></div>`);
    parType.forEach((s) => {
      const isInterval = typeInterval(s.type);
      const isHors = s.type === "Hors plan";
      const rows = [];
      rows.push(`<div class="tr-line"><span>Séances</span><b>${s.nb}</b></div>`);
      rows.push(`<div class="tr-line"><span>Volume</span><b>${km(s.km, 1)} km</b></div>`);
      rows.push(`<div class="tr-line"><span>Durée moy</span><b>${s.nb ? fmtDur(s.durs / s.nb) : "—"}</b></div>`);
      if (s.paceMoy != null && !isHors) {
        let ecart = "";
        if (s.cibleSec) {
          const diff = Math.round(s.paceMoy - s.cibleSec);
          const cls = Math.abs(diff) <= 5 ? "" : diff < 0 ? "up" : "down";
          const sign = diff > 0 ? "+" : "";
          ecart = ` <span class="tr-ecart ${cls}">${sign}${diff}s</span>`;
        }
        rows.push(`<div class="tr-line"><span>Allure moy</span><b>${fmtPace(s.paceMoy)}/km${ecart}</b></div>`);
        if (s.cibleSec) rows.push(`<div class="tr-line"><span>Allure cible</span><b class="muted">${fmtPace(s.cibleSec)}/km</b></div>`);
      } else if (isInterval) {
        rows.push(`<div class="tr-line tr-note"><span class="muted small">Allure moyenne non pertinente (fractionné)</span></div>`);
        if (s.cibleSec) rows.push(`<div class="tr-line"><span>Allure cible bloc</span><b>${fmtPace(s.cibleSec)}/km</b></div>`);
      }
      if (s.fcMoy) rows.push(`<div class="tr-line"><span>FC moy</span><b>${s.fcMoy} bpm</b></div>`);
      grid.appendChild(el(`
        <div class="type-card ${isHors ? "hors" : ""}">
          <div class="tc-head">
            <span class="tc-ic">${typeIcon(s.type === "Renfo" ? "Renfo" : s.type)}</span>
            <span class="tc-name">${escapeHtml(s.type)}</span>
          </div>
          <div class="tc-body">${rows.join("")}</div>
        </div>`));
    });
    root.appendChild(grid);
  }

  /* ---------- Best efforts ---------- */
  root.appendChild(el(`<h2 class="section-title">Meilleurs efforts <span class="sub">estimés depuis l'allure moyenne sur la sortie complète</span></h2>`));
  const eg = el(`<div class="efforts-grid"></div>`);
  [
    { d: 5, l: "5 km" },
    { d: 10, l: "10 km" },
    { d: 15, l: "15 km" },
    { d: 21.0975, l: "Semi" },
    { d: 30, l: "30 km" },
  ].forEach(({ d, l }) => {
    const b = bestEffort(d);
    eg.appendChild(el(`
      <div class="effort-card">
        <div class="e-label">${l}</div>
        <div class="e-val">${b ? fmtDur(b.tempsSec) : "—"}</div>
        <div class="e-pace">${b ? fmtPace(b.paceSec) + " /km" : ""}</div>
        <div class="e-when">${b ? fmtDate(b.date, true) : "aucune séance ≥ " + km(d, 0) + " km"}</div>
      </div>`));
  });
  root.appendChild(eg);

  /* ---------- Répartition volume par type (via plan) ---------- */
  root.appendChild(el(`<h2 class="section-title">Répartition du volume <span class="sub">par type planifié · pendant la prépa</span></h2>`));
  const totalRep = parType.reduce((t, s) => t + s.km, 0);
  const repCard = el(`<div class="card"></div>`);
  if (!totalRep) {
    repCard.appendChild(el(`<div class="muted small">Aucune séance dans la période.</div>`));
  } else {
    parType.slice().sort((a, b) => b.km - a.km).forEach((s) => {
      const pct = Math.round((s.km / totalRep) * 100);
      repCard.appendChild(el(`
        <div style="margin:8px 0">
          <div style="display:flex;justify-content:space-between;font-size:.88rem"><span>${escapeHtml(s.type)}</span><span class="muted">${km(s.km, 0)} km · ${pct}%</span></div>
          <div class="bar"><span style="width:${pct}%"></span></div>
        </div>`));
    });
  }
  root.appendChild(repCard);

  /* ---------- Dernières activités ---------- */
  root.appendChild(el(`<h2 class="section-title">Dernières activités <span class="sub">avec type détecté depuis le plan</span></h2>`));
  const rows = [...joined].reverse().slice(0, 20).map((a) => `
    <tr>
      <td>${fmtDate(a.date, true)}</td>
      <td>${a.planType ? `<span class="type-chip">${escapeHtml(a.planType)}</span>` : `<span class="type-chip hors">Hors plan</span>`}</td>
      <td>${escapeHtml((a.planned && a.planned.titre) || a.titre || a.type || "—")}</td>
      <td>${km(a.distanceKm)}</td>
      <td>${fmtDur(a.dureeSec)}</td>
      <td>${fmtPace(a.allureMoySecKm)}</td>
      <td>${a.fcMoy || "—"}</td>
    </tr>`).join("");
  root.appendChild(el(`
    <div class="table-wrap">
      <table>
        <thead><tr><th>Date</th><th>Type</th><th>Séance</th><th>Km</th><th>Durée</th><th>Allure</th><th>FC</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`));
}

/* ------------------------------------------------------------------ */
/* Journal                                                             */
/* ------------------------------------------------------------------ */
const MOTS_BLESSURE = /\b(blessure|douleur|gêne|gene|tendon|achille|genou|mollet|cheville|contracture|fatigue|repos|kiné|kine)\w*/gi;
const MOTS_METEO = /\b(canicule|chaleur|pluie|vent|froid|neige|humidit[ée])\w*/gi;

function renderJournal() {
  const root = document.getElementById("tab-journal");
  const entries = (JOURNAL || []).filter((e) => e.titre && e.titre.toLowerCase().includes("semaine"));
  if (!entries.length) {
    root.innerHTML = `<div class="empty">Journal vide.<br>Il sera rempli automatiquement par <b>/prepa-update</b>.</div>`;
    return;
  }
  root.innerHTML = "";
  const card = el(`<div class="card"></div>`);
  entries
    .slice()
    .sort((a, b) => (b.semaine || 0) - (a.semaine || 0))
    .forEach((e) => {
      let html = escapeHtml(e.contenu || "");
      html = html.replace(MOTS_METEO, (m) => `<mark class="meteo">${m}</mark>`);
      html = html.replace(MOTS_BLESSURE, (m) => `<mark>${m}</mark>`);
      card.appendChild(el(`
        <div class="jentry">
          <h3>${escapeHtml(e.titre)}</h3>
          <div class="jcontent">${html || '<span class="muted">— vide —</span>'}</div>
        </div>`));
    });
  root.appendChild(card);
}

/* ------------------------------------------------------------------ */
/* Profil                                                              */
/* ------------------------------------------------------------------ */
function renderProfil() {
  const root = document.getElementById("tab-profil");
  if (!OBJ.course) {
    root.innerHTML = `<div class="empty">Aucune prépa initialisée.</div>`;
    return;
  }
  root.innerHTML = "";
  const c = OBJ.course;

  /* Résumé prépa */
  const pairs = [];
  if (c.nom) pairs.push(["Course", c.nom + (c.distanceKm ? ` (${km(c.distanceKm)} km)` : "")]);
  if (c.date) pairs.push(["Date", fmtDate(c.date, true)]);
  if (OBJ.chronoVise) pairs.push(["Objectif", OBJ.chronoVise]);
  if (OBJ.dateDebutPrepa) pairs.push(["Début prépa", fmtDate(OBJ.dateDebutPrepa, true)]);
  if (OBJ.nbSemaines) pairs.push(["Durée", OBJ.nbSemaines + " semaines"]);
  if (OBJ.frequenceSeancesParSemaine) pairs.push(["Fréquence cible", OBJ.frequenceSeancesParSemaine + " séances / sem."]);
  if (OBJ.volumeDepartKmSemaine) pairs.push(["Volume de départ", OBJ.volumeDepartKmSemaine + " km / sem."]);

  root.appendChild(el(`<h2 class="section-title">Prépa</h2>`));
  const card = el(`<div class="card"><div class="pair-list"></div></div>`);
  const pl = card.querySelector(".pair-list");
  pairs.forEach(([k, v]) => {
    pl.appendChild(el(`<div class="k">${escapeHtml(k)}</div>`));
    pl.appendChild(el(`<div class="v">${escapeHtml(v)}</div>`));
  });
  root.appendChild(card);

  /* Références */
  if (OBJ.references && Object.keys(OBJ.references).length) {
    root.appendChild(el(`<h2 class="section-title">Références</h2>`));
    const rc = el(`<div class="card"><div class="pair-list"></div></div>`);
    const rl = rc.querySelector(".pair-list");
    Object.entries(OBJ.references).forEach(([k, v]) => {
      rl.appendChild(el(`<div class="k">${escapeHtml(humanize(k))}</div>`));
      rl.appendChild(el(`<div class="v">${escapeHtml(String(v))}</div>`));
    });
    root.appendChild(rc);
  }

  /* Allures cibles */
  if (OBJ.alluresCibles && Object.keys(OBJ.alluresCibles).length) {
    root.appendChild(el(`<h2 class="section-title">Allures cibles</h2>`));
    const grid = el(`<div class="allures-grid"></div>`);
    Object.entries(OBJ.alluresCibles).forEach(([k, a]) => {
      grid.appendChild(el(`
        <div class="allure-tile">
          <div class="a-key">${escapeHtml(k)}</div>
          <div class="a-val">${escapeHtml(a.affichage || "—")}<span class="unit">/km</span></div>
          ${a.note ? `<div class="a-note">${escapeHtml(a.note)}</div>` : ""}
        </div>`));
    });
    root.appendChild(grid);
  }

  /* Séances signature */
  const favs = OBJ.seancesFavorites || [];
  if (favs.length) {
    root.appendChild(el(`<h2 class="section-title">Séances signature</h2>`));
    const grid = el(`<div class="grid ${favs.length > 1 ? "grid-2" : ""}"></div>`);
    favs.forEach((f) => {
      const meta = [];
      if (f.distanceKm) meta.push("📏 " + km(f.distanceKm) + " km");
      if (f.frequenceSouhaitee) meta.push("🔁 " + escapeHtml(f.frequenceSouhaitee));
      grid.appendChild(el(`
        <div class="card">
          <div class="stitle" style="font-weight:650">
            <span class="stype" style="width:auto;padding:4px 10px">${escapeHtml(f.type || "?")}</span>
            <span>${escapeHtml(f.nom || "Séance")}</span>
          </div>
          ${f.description ? `<div class="sdesc" style="margin-top:8px">${escapeHtml(f.description)}</div>` : ""}
          ${meta.length ? `<div class="sallure" style="margin-top:8px">${meta.join(" · ")}</div>` : ""}
          ${f.contexte ? `<div class="muted small" style="margin-top:6px">${escapeHtml(f.contexte)}</div>` : ""}
        </div>`));
    });
    root.appendChild(grid);
  }

  /* Renforcement */
  if (OBJ.renforcement) {
    const r = OBJ.renforcement;
    root.appendChild(el(`<h2 class="section-title">Renforcement</h2>`));
    const rc = el(`<div class="card"></div>`);
    const line = [];
    if (r.actif != null) line.push(r.actif ? "Actif" : "Inactif");
    if (r.frequenceParSemaine) line.push(r.frequenceParSemaine + "×/sem.");
    if (line.length) rc.appendChild(el(`<div style="font-weight:600;margin-bottom:6px">${escapeHtml(line.join(" · "))}</div>`));
    if (r.materiel && r.materiel.length) {
      const chips = el(`<div class="chips" style="margin-bottom:8px"></div>`);
      r.materiel.forEach((m) => chips.appendChild(el(`<span class="chip">${escapeHtml(m)}</span>`)));
      rc.appendChild(chips);
    }
    if (r.focus) rc.appendChild(el(`<div class="sdesc">${escapeHtml(r.focus)}</div>`));
    root.appendChild(rc);
  }

  /* Blessures */
  if (OBJ.blessures && OBJ.blessures.length) {
    root.appendChild(el(`<h2 class="section-title">Blessures suivies</h2>`));
    OBJ.blessures.forEach((b) => {
      const zone = typeof b === "string" ? b : b.zone;
      const box = el(`<div class="callout danger" style="margin-bottom:10px"></div>`);
      box.appendChild(el(`<div style="font-weight:650">🩹 ${escapeHtml(zone || "—")}</div>`));
      if (b && b.statut) box.appendChild(el(`<div class="muted small" style="margin-top:4px">${escapeHtml(b.statut)}</div>`));
      if (b && b.consignes) box.appendChild(el(`<div style="margin-top:6px;font-size:.9rem">${escapeHtml(b.consignes)}</div>`));
      root.appendChild(box);
    });
  }

  /* Contraintes */
  if (OBJ.contraintes && OBJ.contraintes.length) {
    root.appendChild(el(`<h2 class="section-title">Contraintes</h2>`));
    OBJ.contraintes.forEach((c) => {
      const box = el(`<div class="callout warn" style="margin-bottom:10px"></div>`);
      const head = [c.type, c.periode].filter(Boolean).map(escapeHtml).join(" · ");
      if (head) box.appendChild(el(`<div style="font-weight:650">${head}</div>`));
      if (c.detail) box.appendChild(el(`<div style="margin-top:4px;font-size:.9rem">${escapeHtml(c.detail)}</div>`));
      root.appendChild(box);
    });
  }

  /* Commentaires libres */
  if (OBJ.commentairesLibres) {
    root.appendChild(el(`<h2 class="section-title">Note libre</h2>`));
    root.appendChild(el(`<div class="card"><div class="sdesc">${escapeHtml(OBJ.commentairesLibres)}</div></div>`));
  }
}

/* ------------------------------------------------------------------ */
/* Sélecteur + tabs + thème                                            */
/* ------------------------------------------------------------------ */
const RENDERERS = { dashboard: renderDashboard, stats: renderStats, journal: renderJournal, profil: renderProfil };
let RENDERED = {};
let CURRENT_TAB = "dashboard";

function loadSelection() {
  try {
    const s = JSON.parse(localStorage.getItem(LS_KEY) || "null");
    if (s && s.profil && s.prepa) return s;
  } catch (_) {}
  return null;
}
function saveSelection() {
  if (!CURRENT.profil || !CURRENT.prepa) return;
  localStorage.setItem(LS_KEY, JSON.stringify({ profil: CURRENT.profil.id, prepa: CURRENT.prepa.id }));
}

function findDefault() {
  const saved = loadSelection();
  const profils = CATALOG.profils || [];
  if (saved) {
    const p = profils.find((x) => x.id === saved.profil);
    const pr = p && (p.prepas || []).find((x) => x.id === saved.prepa);
    if (p && pr) return { profil: p, prepa: pr };
  }
  const p = profils[0];
  if (!p) return { profil: null, prepa: null };
  const active = (p.prepas || []).find((x) => x.id === p.prepaActive);
  return { profil: p, prepa: active || (p.prepas || [])[0] || null };
}

function applySelection(profil, prepa) {
  CURRENT = { profil, prepa };
  OBJ = (prepa && prepa.objectifs) || {};
  PLAN = (prepa && prepa.plan) || { semaines: [] };
  ACTS = (prepa && prepa.activites) || [];
  JOURNAL = (prepa && prepa.journal) || [];
  VIEW_WEEK_IDX = null;
  RENDERED = {};
  saveSelection();
  renderHeader();
  activateTab(CURRENT_TAB);
}

function populateSelectors() {
  const selProfil = document.getElementById("sel-profil");
  const selPrepa = document.getElementById("sel-prepa");
  const profils = CATALOG.profils || [];

  selProfil.innerHTML = "";
  profils.forEach((p) => {
    const opt = document.createElement("option");
    opt.value = p.id;
    opt.textContent = p.nom;
    selProfil.appendChild(opt);
  });

  function fillPrepas(profil) {
    selPrepa.innerHTML = "";
    (profil.prepas || []).forEach((pr) => {
      const opt = document.createElement("option");
      opt.value = pr.id;
      const c = (pr.objectifs && pr.objectifs.course) || {};
      opt.textContent = c.nom ? `${c.nom}${c.date ? " · " + c.date : ""}` : pr.nom || pr.id;
      selPrepa.appendChild(opt);
    });
  }

  const def = findDefault();
  if (!def.profil) {
    document.getElementById("course-nom").textContent = "Aucun profil";
    return;
  }
  selProfil.value = def.profil.id;
  fillPrepas(def.profil);
  if (def.prepa) selPrepa.value = def.prepa.id;
  applySelection(def.profil, def.prepa);

  selProfil.addEventListener("change", () => {
    const p = profils.find((x) => x.id === selProfil.value);
    if (!p) return;
    fillPrepas(p);
    const active = (p.prepas || []).find((x) => x.id === p.prepaActive) || (p.prepas || [])[0] || null;
    if (active) selPrepa.value = active.id;
    applySelection(p, active);
  });

  selPrepa.addEventListener("change", () => {
    const p = profils.find((x) => x.id === selProfil.value);
    if (!p) return;
    const pr = (p.prepas || []).find((x) => x.id === selPrepa.value);
    applySelection(p, pr);
  });
}

function activateTab(name) {
  CURRENT_TAB = name;
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
  document.querySelectorAll(".tab-panel").forEach((p) => p.classList.toggle("active", p.id === "tab-" + name));
  if (!RENDERED[name]) { RENDERERS[name](); RENDERED[name] = true; }
}
function setupTabs() {
  document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => activateTab(t.dataset.tab)));
}

/* Thème clair / sombre */
function currentTheme() {
  return document.documentElement.getAttribute("data-theme") || "dark";
}
function applyTheme(t) {
  document.documentElement.setAttribute("data-theme", t);
  try { localStorage.setItem(LS_THEME, t); } catch (_) {}
  const btn = document.getElementById("theme-toggle");
  if (btn) btn.textContent = t === "light" ? "☀️" : "🌙";
  refreshChartsTheme();
  RENDERED = {};
  activateTab(CURRENT_TAB);
}
function setupTheme() {
  const btn = document.getElementById("theme-toggle");
  if (!btn) return;
  btn.textContent = currentTheme() === "light" ? "☀️" : "🌙";
  btn.addEventListener("click", () => applyTheme(currentTheme() === "light" ? "dark" : "light"));
}

/* Init */
setupTabs();
setupTheme();
populateSelectors();
