const brandEl = document.getElementById('brand');
const versionBtn = document.getElementById('versionBtn');
const versionLabel = document.getElementById('versionLabel');
const versionMenu = document.getElementById('versionMenu');
const picker = document.getElementById('picker');
const injectBtn = document.getElementById('inject');
const statusEl = document.getElementById('status');
const minBtn = document.getElementById('minBtn');
const closeBtn = document.getElementById('closeBtn');

let selected = '26.3';

function setStatus(text, kind) {
  statusEl.textContent = text;
  statusEl.className = 'status' + (kind ? ' ' + kind : '');
}

function closeMenu() {
  versionMenu.hidden = true;
  picker.classList.remove('open');
  versionBtn.setAttribute('aria-expanded', 'false');
}

function openMenu() {
  versionMenu.hidden = false;
  picker.classList.add('open');
  versionBtn.setAttribute('aria-expanded', 'true');
}

function renderVersions(versions, defaultVersion) {
  selected = defaultVersion && versions.includes(defaultVersion) ? defaultVersion : versions[0];
  versionLabel.textContent = selected;
  versionMenu.innerHTML = '';
  versions.forEach((v) => {
    const li = document.createElement('li');
    li.setAttribute('role', 'option');
    li.tabIndex = 0;
    li.textContent = v;
    li.dataset.value = v;
    if (v === selected) {
      li.classList.add('active');
    }
    li.addEventListener('click', () => {
      selected = v;
      versionLabel.textContent = v;
      versionMenu.querySelectorAll('li').forEach((item) => item.classList.toggle('active', item.dataset.value === v));
      closeMenu();
    });
    versionMenu.appendChild(li);
  });
}

versionBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  if (versionMenu.hidden) {
    openMenu();
  } else {
    closeMenu();
  }
});

document.addEventListener('click', (e) => {
  if (!picker.contains(e.target)) {
    closeMenu();
  }
});

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    closeMenu();
  }
});

minBtn.addEventListener('click', () => window.ghost.minimize());
closeBtn.addEventListener('click', () => window.ghost.close());

injectBtn.addEventListener('click', async () => {
  injectBtn.disabled = true;
  closeMenu();
  setStatus('Injecting…');
  try {
    const result = await window.ghost.inject(selected);
    setStatus(result.message || (result.ok ? 'Done.' : 'Failed.'), result.ok ? 'ok' : 'bad');
  } catch (err) {
    setStatus('Inject failed.', 'bad');
  } finally {
    injectBtn.disabled = false;
  }
});

async function boot() {
  const meta = await window.ghost.getMeta();
  brandEl.textContent = meta.brand;
  renderVersions(meta.versions, meta.defaultVersion);
  setStatus('Start Minecraft, then inject');
}

boot();
