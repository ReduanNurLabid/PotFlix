const { app, BrowserWindow, shell } = require('electron');
const path = require('path');
const http = require('http');

const PORT = 3000;
const APP_URL = `http://localhost:${PORT}`;
let mainWindow;

// Check if server is already running
function isServerRunning() {
    return new Promise((resolve) => {
        const req = http.get(APP_URL, () => resolve(true));
        req.on('error', () => resolve(false));
        req.setTimeout(1500, () => { req.destroy(); resolve(false); });
    });
}

async function startServer() {
    const running = await isServerRunning();
    if (running) {
        console.log('Server already running on port ' + PORT);
        return;
    }

    try {
        require('./server');
        await new Promise(r => setTimeout(r, 1000));
    } catch (err) {
        console.error('Failed to start server:', err.message);
    }
}

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1400,
        height: 900,
        minWidth: 900,
        minHeight: 600,
        backgroundColor: '#0a0a0a',
        icon: path.join(__dirname, 'public', 'icon.png'),
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
        },
        frame: true,
        titleBarStyle: 'hidden',
        titleBarOverlay: {
            color: '#0a0a0a',
            symbolColor: '#e5e5e5',
            height: 36
        },
        show: false
    });

    mainWindow.loadURL(APP_URL);

    mainWindow.once('ready-to-show', () => {
        mainWindow.show();
    });

    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        if (url.startsWith('http')) {
            shell.openExternal(url);
        }
        return { action: 'deny' };
    });

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

app.whenReady().then(async () => {
    await startServer();
    createWindow();
});

app.on('window-all-closed', () => {
    app.quit();
});

app.on('activate', () => {
    if (mainWindow === null) {
        createWindow();
    }
});
