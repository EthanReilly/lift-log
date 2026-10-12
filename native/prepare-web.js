// Copies the web app (repo root) into www/ for the Android build.
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const www = path.join(__dirname, 'www');
fs.rmSync(www, { recursive: true, force: true });
fs.mkdirSync(path.join(www, 'icons'), { recursive: true });
let html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
if (!html.includes('<!--CAP-->')) throw new Error('index.html is missing the <!--CAP--> marker');
html = html.replace('<!--CAP-->', '<script src="capacitor.js"></script>');
fs.writeFileSync(path.join(www, 'index.html'), html);
fs.copyFileSync(path.join(__dirname, 'node_modules/@capacitor/core/dist/capacitor.js'), path.join(www, 'capacitor.js'));
for (const f of fs.readdirSync(path.join(root, 'icons'))) fs.copyFileSync(path.join(root, 'icons', f), path.join(www, 'icons', f));
console.log('www ready');
