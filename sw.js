const CACHE='lift-log-v2';
const FILES=['./','index.html','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(CACHE).then(c=>c.addAll(FILES)).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==CACHE).map(x=>caches.delete(x)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{
  if(e.request.method!=='GET')return;
  const isPage=e.request.mode==='navigate';
  if(isPage){
    // network first so updates arrive, cache when offline
    e.respondWith(fetch(e.request).then(res=>{const c=res.clone();caches.open(CACHE).then(ch=>ch.put('index.html',c));return res}).catch(()=>caches.match('index.html')));
    return;
  }
  e.respondWith(caches.match(e.request,{ignoreSearch:true}).then(r=>r||fetch(e.request).then(res=>{const c=res.clone();caches.open(CACHE).then(ch=>ch.put(e.request,c));return res})));
});
