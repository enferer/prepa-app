/* app.js — rendu multi-profils / multi-prépas.
   Lit window.PREPA_DATA = { genereLe, profils: [{ id, nom, prepaActive, prepas: [...] }] }.
   Le sélecteur profil+prépa pilote le rendu ; la sélection est persistée en localStorage. */

const CATALOG = window.PREPA_DATA || { profils: [] };
const LS_KEY = "prepa.selection";

/* État courant — recalculé à chaque changement de sélecteur. */
let CURRENT = { profil: null, prepa: null };
let OBJ = {};
let PLAN = { semaines: [] };
let ACTS = [];
let JOURNAL = [];

/* ------------------------------------------------------------------ */
/* Helpers de formatage                                                */
/* ------------------------------------------------------------------ */
const MOIS = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."];
const JOURS = ["dim.", "lun.", "mar.", "mer.", "jeu.", "ven.", "sam."];

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
  if (type === "Côtes") return "⛰️";
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
  document.getElementById("course-nom").textContent = profilNom ? `${profilNom} · ${prepaNom}` : prepaNom;
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
/* Onglet Dashboard                                                    */
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

  const alerts = buildAlerts();
  if (alerts.length) {
    const box = el(`<div style="margin-top:16px"></div>`);
    alerts.forEach((a) => box.appendChild(el(
      `<div class="alert ${a.type}"><span class="alert-icon">${a.icon}</span><span>${a.msg}</span></div>`)));
    root.appendChild(box);
  }

  const s = toutesSeances();
  const faites = s.filter((x) => STATUT_FAIT.includes(x.statut));
  const manquees = s.filter((x) => x.statut === "manquee");
  const restantes = s.filter((x) => x.statut === "a_venir");
  const resolu = faites.length + manquees.length;
  const assidu = resolu ? Math.round((faites.length / resolu) * 100) : null;

  const debut = OBJ.dateDebutPrepa;
  const kmDurant = debut ? sommeKm((a) => a.date && a.date >= debut) : sommeKm(() => true);
  const kmAvant = debut ? sommeKm((a) => a.date && a.date < debut) : 0;
  const kmCibleTotal = (PLAN.semaines || []).reduce((t, w) => t + (w.volumeCibleKm || 0), 0);

  const kpis = [
    { icon: "✅", val: faites.length, label: "Séances faites" },
    { icon: "🗓️", val: restantes.length, label: "Séances restantes" },
    { icon: "⛔", val: manquees.length, label: "Séances loupées", danger: manquees.length > 0 },
    { icon: "🎯", val: assidu != null ? assidu + "%" : "—", label: "Assiduité" },
    { icon: "🏃", val: km(kmDurant, 0), unit: "km", label: "Km depuis le début", sub: kmCibleTotal ? "cible " + km(kmCibleTotal, 0) + " km" : "" },
    { icon: "📦", val: km(kmAvant, 0), unit: "km", label: "Km avant la prépa" },
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

  const prochaines = restantes
    .filter((x) => x.date && parseISO(x.date) >= today)
    .sort(byDate)
    .slice(0, 5);
  root.appendChild(el(`<h2 class="section-title">Prochaines séances</h2>`));
  if (prochaines.length) {
    const box = el(`<div></div>`);
    prochaines.forEach((p) => box.appendChild(renderSeance(p)));
    root.appendChild(box);
  } else {
    root.appendChild(el(`<div class="card muted">Aucune séance à venir programmée. Lance <b>/prepa-update</b> ou <b>/prepa-init</b>.</div>`));
  }

  if (manquees.length) {
    root.appendChild(el(`<h2 class="section-title">Séances loupées</h2>`));
    const box = el(`<div></div>`);
    manquees.slice().sort(byDate).forEach((m) => box.appendChild(renderSeance(m)));
    root.appendChild(box);
  }

  renderSignature(root);

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

function renderSignature(root) {
  const favs = OBJ.seancesFavorites || [];
  const libre = OBJ.commentairesLibres;
  if (!favs.length && !libre) return;

  root.appendChild(el(`<h2 class="section-title">Mes séances signature</h2>`));
  if (favs.length) {
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
  if (libre) {
    root.appendChild(el(`
      <div class="card" style="margin-top:12px">
        <div class="muted small" style="text-transform:uppercase;letter-spacing:.4px;margin-bottom:6px">Note libre</div>
        <div>${escapeHtml(libre)}</div>
      </div>`));
  }
}

function buildAlerts() {
  const out = [];
  (OBJ.blessures || []).forEach((b) => {
    const txt = typeof b === "string" ? b : b.zone || JSON.stringify(b);
    out.push({ type: "danger", icon: "🩹", msg: "Blessure suivie : " + txt });
  });
  let manquees = 0;
  (PLAN.semaines || []).forEach((s) => (s.seances || []).forEach((se) => { if (se.statut === "manquee") manquees++; }));
  if (manquees) out.push({ type: "warn", icon: "⚠️", msg: `${manquees} séance${manquees > 1 ? "s" : ""} manquée${manquees > 1 ? "s" : ""} — pense à en discuter avec le coach (/prepa-update).` });
  const sc = semaineCourante();
  if (sc) {
    const j = JOURNAL.find((e) => e.semaine === sc.numero);
    if (!j || !j.contenu.trim()) out.push({ type: "ok", icon: "📝", msg: `Journal de la semaine ${sc.numero} vide — note ton ressenti dans data/journal.md.` });
  }
  return out;
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

function renderPlan() {
  const root = document.getElementById("tab-plan");
  const sems = PLAN.semaines || [];
  if (!sems.length) {
    root.innerHTML = `<div class="empty">Aucun plan pour l'instant.<br>Lance <b>/prepa-init</b> pour le générer.</div>`;
    return;
  }
  root.innerHTML = "";

  root.appendChild(el(`
    <div class="legend card">
      <div class="legend-row"><span class="legend-title">Types</span><span>🏃 Course</span><span>⛰️ Côtes</span><span>💪 Renfo</span></div>
      <div class="legend-row"><span class="legend-title">Statuts</span><span class="badge validee">Validée</span><span class="badge a_venir">À venir</span><span class="badge adaptee">Adaptée</span><span class="badge manquee">Manquée</span></div>
    </div>`));

  const sc = semaineCourante();
  sems.forEach((s) => {
    const seances = s.seances || [];
    const course = seances.filter((x) => x.type !== "Renfo");
    const renfo = seances.filter((x) => x.type === "Renfo");
    const isCurrent = sc && s.numero === sc.numero;
    const real = volumeRealise(s.dateDebut);
    const done = seances.filter((x) => STATUT_FAIT.includes(x.statut)).length;

    const fin = parseISO(s.dateDebut);
    let finStr = "";
    if (fin) { fin.setDate(fin.getDate() + 6); finStr = " → " + fmtDate(fin.toISOString().slice(0, 10)); }

    const wrap = el(`<div class="week${isCurrent ? " open" : ""}"></div>`);
    wrap.appendChild(el(`
      <button class="week-head">
        <div class="wk-left">
          <div class="week-bloc">${escapeHtml(s.bloc || "")}</div>
          <div class="wk-title">Semaine ${s.numero}${isCurrent ? ' <span class="wk-now">en cours</span>' : ""}</div>
          <div class="wk-meta">${fmtDate(s.dateDebut)}${finStr} · 🏃 ${course.length} · 💪 ${renfo.length} · 📏 ${km(real)}/${km(s.volumeCibleKm, 0)} km · ✅ ${done}/${seances.length}</div>
        </div>
        <span class="chevron">▶</span>
      </button>`));

    const body = el(`<div class="week-body"></div>`);
    if (s.note) body.appendChild(el(`<p class="wk-note muted small">${escapeHtml(s.note)}</p>`));
    if (course.length) {
      body.appendChild(el(`<div class="grp-title">🏃 Course</div>`));
      course.slice().sort(byDate).forEach((se) => body.appendChild(renderSeance(se)));
    }
    if (renfo.length) {
      body.appendChild(el(`<div class="grp-title">💪 Renforcement</div>`));
      renfo.slice().sort(byDate).forEach((se) => body.appendChild(renderSeance(se)));
    }
    wrap.appendChild(body);
    wrap.querySelector(".week-head").addEventListener("click", () => wrap.classList.toggle("open"));
    root.appendChild(wrap);
  });
}

function renderStats() {
  const root = document.getElementById("tab-stats");
  if (!ACTS.length) {
    root.innerHTML = `<div class="empty">Aucune activité.<br>Colle tes séances Garmin dans <b>data/garmin.csv</b> puis lance <code>python3 scripts/build_data.py</code>.</div>`;
    return;
  }
  root.innerHTML = "";
  const labels = ACTS.map((a) => fmtDate(a.date));

  root.appendChild(el(`<h2 class="section-title">Distance par séance</h2>`));
  root.appendChild(el(`<div class="card"><div class="chart-box short"><canvas id="cv-dist"></canvas></div></div>`));

  root.appendChild(el(`<h2 class="section-title">Allure moyenne (min/km)</h2>`));
  root.appendChild(el(`<div class="card"><div class="chart-box short"><canvas id="cv-allure"></canvas></div></div>`));

  root.appendChild(el(`<div class="grid grid-2" style="margin-top:16px"></div>`));
  const grid = root.querySelector(".grid-2");
  grid.appendChild(el(`<div class="card"><h3 class="small muted" style="margin-bottom:10px">Fréquence cardiaque moyenne</h3><div class="chart-box short"><canvas id="cv-fc"></canvas></div></div>`));
  grid.appendChild(el(`<div class="card"><h3 class="small muted" style="margin-bottom:10px">Cadence moyenne (ppm)</h3><div class="chart-box short"><canvas id="cv-cad"></canvas></div></div>`));

  root.appendChild(el(`<h2 class="section-title">Dernières activités</h2>`));
  const rows = [...ACTS].reverse().map((a) => `
    <tr>
      <td>${fmtDate(a.date, true)}</td>
      <td>${a.titre || a.type || "—"}</td>
      <td>${km(a.distanceKm)}</td>
      <td>${fmtDur(a.dureeSec)}</td>
      <td>${fmtPace(a.allureMoySecKm)}</td>
      <td>${a.fcMoy || "—"}</td>
      <td>${a.cadenceMoy || "—"}</td>
    </tr>`).join("");
  root.appendChild(el(`
    <div class="table-wrap">
      <table>
        <thead><tr><th>Date</th><th>Séance</th><th>Km</th><th>Durée</th><th>Allure</th><th>FC</th><th>Cad.</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>`));

  chartLine("dist", document.getElementById("cv-dist"), labels, ACTS.map((a) => a.distanceKm), "Distance (km)", CHART_COLORS.accent);
  chartAllure(document.getElementById("cv-allure"), labels, ACTS.map((a) => a.allureMoySecKm));
  chartLine("fc", document.getElementById("cv-fc"), labels, ACTS.map((a) => a.fcMoy), "FC moy", CHART_COLORS.ok);
  chartLine("cad", document.getElementById("cv-cad"), labels, ACTS.map((a) => a.cadenceMoy), "Cadence", CHART_COLORS.warn);
}

const MOTS_BLESSURE = /\b(blessure|douleur|gêne|gene|tendon|achille|genou|mollet|cheville|contracture|fatigue|repos|kiné|kine)\w*/gi;
const MOTS_METEO = /\b(canicule|chaleur|pluie|vent|froid|neige|humidit[ée])\w*/gi;

function renderJournal() {
  const root = document.getElementById("tab-journal");
  const entries = (JOURNAL || []).filter((e) => e.titre && e.titre.toLowerCase().includes("semaine"));
  if (!entries.length) {
    root.innerHTML = `<div class="empty">Journal vide.<br>Écris ton ressenti par semaine dans <b>data/journal.md</b> (sections <code>## Semaine N</code>).</div>`;
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

function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/* ------------------------------------------------------------------ */
/* Sélecteur profil / prépa                                            */
/* ------------------------------------------------------------------ */
const RENDERERS = { dashboard: renderDashboard, plan: renderPlan, stats: renderStats, journal: renderJournal };
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

/* ------------------------------------------------------------------ */
/* Navigation onglets                                                  */
/* ------------------------------------------------------------------ */
function activateTab(name) {
  CURRENT_TAB = name;
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
  document.querySelectorAll(".tab-panel").forEach((p) => p.classList.toggle("active", p.id === "tab-" + name));
  if (!RENDERED[name]) { RENDERERS[name](); RENDERED[name] = true; }
}
function setupTabs() {
  document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => activateTab(t.dataset.tab)));
}

/* Init */
setupTabs();
populateSelectors();
