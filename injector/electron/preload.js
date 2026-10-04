const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('ghost', {
  getMeta: () => ipcRenderer.invoke('get-meta'),
  inject: (version) => ipcRenderer.invoke('inject', version),
  minimize: () => ipcRenderer.invoke('window-minimize'),
  close: () => ipcRenderer.invoke('window-close')
});
