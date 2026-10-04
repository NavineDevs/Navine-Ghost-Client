const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

const PRODUCT = process.env.GHOST_BRAND || 'NAVINE';
const PRODUCT_TITLE = process.env.GHOST_PRODUCT || 'NavineGhostClientInjector';
const AGENT_CLASS = process.env.GHOST_AGENT_CLASS || 'nv.navineghost.injector.GhostAgent';

const VERSIONS = ['1.21.11', '26', '26.1', '26.1.2', '26.2', '26.3'];

const EXIT_TEXT = {
  0: 'Injected. Press ` in-game.',
  2: 'Missing inject resources. Rebuild the injector.',
  3: 'Could not prepare inject config.',
  4: 'Minecraft is not running. Start the game first.',
  5: 'Inject failed. Run as administrator and retry.'
};

function resourceRoot() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'resources');
  }
  return path.join(__dirname, 'resources');
}

function cliPath() {
  const root = resourceRoot();
  const names = ['runtime-helper.exe', 'ghost-inject-cli.exe'];
  for (const name of names) {
    const full = path.join(root, name);
    if (fs.existsSync(full)) {
      return full;
    }
  }
  return path.join(root, 'ghost-inject-cli.exe');
}

function validateInstall() {
  if (!app.isPackaged) {
    return null;
  }
  const requiredBesideExe = [
    'resources.pak',
    'icudtl.dat',
    'ffmpeg.dll',
    path.join('resources', 'app.asar')
  ];
  const base = path.dirname(process.execPath);
  for (const rel of requiredBesideExe) {
    if (!fs.existsSync(path.join(base, rel))) {
      return 'This app cannot run from the .exe alone.\n\nZip and send the entire NavineGhostClientInjector folder (or use NavineGhostClientInjector-Portable.exe).\n\nMissing: ' + rel;
    }
  }
  const rr = resourceRoot();
  if (!fs.existsSync(path.join(rr, 'payload.jar')) && !fs.existsSync(path.join(rr, 'agent.jar'))) {
    return 'Inject payload files are missing from resources.\n\nRe-download the full package folder or Portable build.';
  }
  return null;
}

function createWindow() {
  const win = new BrowserWindow({
    width: 400,
    height: 430,
    resizable: false,
    maximizable: false,
    minimizable: true,
    fullscreenable: false,
    frame: false,
    transparent: false,
    backgroundColor: '#0a0d12',
    title: PRODUCT_TITLE,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  win.removeMenu();
  win.once('ready-to-show', () => win.show());
  win.loadFile(path.join(__dirname, 'index.html'));
}

app.whenReady().then(() => {
  const problem = validateInstall();
  if (problem) {
    dialog.showErrorBox(PRODUCT_TITLE + ' cannot start', problem);
    app.quit();
    return;
  }
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

process.on('uncaughtException', (err) => {
  try {
    dialog.showErrorBox(PRODUCT_TITLE + ' crashed', String(err && err.stack ? err.stack : err));
  } catch (_) {
  }
});

ipcMain.handle('get-meta', () => ({
  brand: PRODUCT,
  title: PRODUCT_TITLE,
  versions: VERSIONS,
  defaultVersion: '26.3'
}));

ipcMain.handle('window-minimize', (event) => {
  BrowserWindow.fromWebContents(event.sender)?.minimize();
});

ipcMain.handle('window-close', (event) => {
  BrowserWindow.fromWebContents(event.sender)?.close();
});

ipcMain.handle('inject', async (_event, version) => {
  const resources = resourceRoot();
  const cli = cliPath();
  if (!fs.existsSync(cli)) {
    return { ok: false, message: 'Missing inject helper. Use the full package folder or Portable build.' };
  }
  return await new Promise((resolve) => {
    const child = spawn(cli, [
      '--version', String(version || '26.1.2'),
      '--resources', resources,
      '--agent-class', AGENT_CLASS
    ], {
      windowsHide: true,
      cwd: resources,
      stdio: 'ignore'
    });
    child.on('error', () => {
      resolve({ ok: false, message: 'Could not start inject helper. Antivirus may be blocking it.' });
    });
    child.on('close', (code) => {
      const n = typeof code === 'number' ? code : 5;
      resolve({
        ok: n === 0,
        message: EXIT_TEXT[n] || 'Inject failed.',
        code: n
      });
    });
  });
});
