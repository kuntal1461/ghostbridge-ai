import puppeteer from 'puppeteer-core';
import fs from 'fs';
// Credentials come from the environment only: DEMO_PASSWORD (never written to disk or printed). CHROME_PATH overrides the browser.
const PW = process.env.DEMO_PASSWORD; if (!PW) { console.error('set DEMO_PASSWORD'); process.exit(2); }
const results = []; const calls = []; const origins = new Set();
const ok = (name, cond, extra='') => { results.push([cond?'PASS':'FAIL', name, extra]); console.log((cond?'PASS ':'FAIL ')+name+(extra?'  ['+extra+']':'')); };
const sleep = ms => new Promise(r=>setTimeout(r,ms));
const b = await puppeteer.launch({executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless:'new', acceptInsecureCerts:true, args:['--ignore-certificate-errors']});
const p = await b.newPage(); await p.setViewport({width:1280,height:900});
p.on('response', r => { const u = new URL(r.url()); origins.add(u.origin); if (u.pathname.startsWith('/api/')) calls.push(`${r.request().method()} ${u.pathname} ${r.status()}`); });
p.on('pageerror', e => console.log('PAGEERROR', e.message));
const text = () => p.evaluate(()=>document.body.innerText);
const fillByLabel = async (label, value) => { const id = await p.evaluate(l=>{const e=[...document.querySelectorAll('input,textarea,select')].find(x=>(x.labels?.[0]?.textContent||'').trim().startsWith(l)); return e?e.id:null;}, label); await p.evaluate(i=>document.getElementById(i).focus(), id); await p.keyboard.type(value); };
const clickText = async t => { const h = await p.evaluateHandle(t=>[...document.querySelectorAll('button,a')].find(e=>e.textContent.trim()===t), t); await h.asElement().click(); };
const nav = async path => { await p.goto('https://localhost'+path,{waitUntil:'networkidle0'}); };

// 1 sign-in page renders; protected route redirects
await nav('/usage');
ok('protected route redirects anonymous browser to /login', p.url().endsWith('/login'), p.url());
ok('sign-in page renders', (await text()).includes('Sign in') && !!(await p.$('#username')));
await p.screenshot({path:'shots/01-login.png'});
// 2 wrong password
await p.type('#username','demo'); await p.type('#password','definitely-wrong');
await p.click('button[type=submit]'); await sleep(1500);
ok('wrong password shows an error and stays on /login', p.url().endsWith('/login') && /invalid|failed|incorrect|could not/i.test(await text()), (await text()).split('\n').filter(l=>/invalid|failed|incorrect/i.test(l))[0]);
ok('password field cleared after failure', (await p.$eval('#password',e=>e.value))==='');
await p.screenshot({path:'shots/02-login-error.png'});
// 3 correct login lands on the originally requested page
await p.type('#password',PW); await p.click('button[type=submit]'); await p.waitForFunction(()=>location.pathname!=='/login',{timeout:15000}); await sleep(1500);
ok('login works and returns to the requested page (/usage)', p.url().endsWith('/usage'), p.url());
const cookieVisible = await p.evaluate(()=>document.cookie);
ok('session cookie is HttpOnly (not readable by page script)', !/JSESSIONID/.test(cookieVisible));
const cks = await b.cookies(); const sc = cks.find(c=>c.name==='JSESSIONID');
ok('session cookie flags Secure/HttpOnly/SameSite=Strict', !!sc && sc.secure && sc.httpOnly && sc.sameSite==='Strict', JSON.stringify({secure:sc?.secure,httpOnly:sc?.httpOnly,sameSite:sc?.sameSite}));
const storage = await p.evaluate(()=>({l:localStorage.length,s:sessionStorage.length}));
ok('nothing stored in localStorage/sessionStorage', storage.l===0 && storage.s===0);
ok('usage page renders', /Usage/.test(await text()));
await p.screenshot({path:'shots/03-usage.png'});
// 4 pages
await nav('/'); ok('dashboard renders', /Dashboard/.test(await text())); await p.screenshot({path:'shots/04-dashboard.png'});
await nav('/provider'); const pt = await text(); ok('provider status shows ollama, phi3:3.8b and an available state', /ollama/i.test(pt) && pt.includes('phi3:3.8b') && /available/i.test(pt)); await p.screenshot({path:'shots/05-provider.png'});
await nav('/playground'); ok('AI playground renders', /Playground/.test(await text()));
// 5 structured request: real local inference
await nav('/structured');
await fillByLabel('Variable 1 value','GhostBridge is a self-hosted AI app. It keeps all inference on a local model running next to the application.');
// error state first: a variable name the prompt does not use
await fillByLabel('Variable 1 name','x');
await p.evaluate(()=>{const e=[...document.querySelectorAll('input')].find(x=>(x.labels?.[0]?.textContent||'').startsWith('Variable 1 name')); e.focus(); e.select();});
await p.keyboard.type('wrongname');
await clickText('Generate JSON'); await sleep(3000);
const et = await text();
ok('server validation error is shown with a reference id (no stack, no internals)', /Missing template variable/.test(et) && /Reference/.test(et) && !/Exception|at com\./.test(et));
await p.screenshot({path:'shots/06a-structured-error.png'});
await p.evaluate(()=>{const e=[...document.querySelectorAll('input')].find(x=>(x.labels?.[0]?.textContent||'').startsWith('Variable 1 name')); e.focus(); e.select();});
await p.keyboard.type('text');
await clickText('Generate JSON');
await p.waitForFunction(()=>/Structured JSON/.test(document.body.innerText),{timeout:170000}).catch(()=>{});
const st = await text();
ok('structured request returns JSON from the local model', /Structured JSON/.test(st) && /summary/.test(st));
const tu = st.match(/Input tokens\s*\n\s*(\d+)[\s\S]*?Output tokens\s*\n\s*(\d+)[\s\S]*?Total tokens\s*\n\s*(\d+)/i);
ok('token usage (input/output/total) is displayed for the request', !!tu && +tu[3] === +tu[1] + +tu[2], tu ? `in=${tu[1]} out=${tu[2]} total=${tu[3]}` : 'not found');
await p.screenshot({path:'shots/06-structured-result.png'});
await nav('/usage'); const ut = await text(); const um = ut.match(/Requests\s*\n\s*(\d+)[\s\S]*?Succeeded\s*\n\s*(\d+)[\s\S]*?Failed\s*\n\s*(\d+)[\s\S]*?Total tokens\s*\n\s*(\d+)/i);
ok('usage page shows request counts and token totals for the caller', !!um && +um[1] >= 1 && +um[2] >= 1 && +um[4] > 0, um ? `requests=${um[1]} ok=${um[2]} failed=${um[3]} totalTokens=${um[4]}` : 'not found');
await p.screenshot({path:'shots/07-usage-after.png'});
// 6 CSRF handling
const csrfStatus = await p.evaluate(async()=> (await fetch('/api/v1/ai/generate',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"promptName":"generic-structured","variables":{"text":"x"}}'})).status);
ok('state-changing call without CSRF token is refused (403)', csrfStatus===403, String(csrfStatus));
const forged = await p.evaluate(async()=> (await fetch('/api/v1/ai/generate',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':'forged'},body:'{"promptName":"generic-structured","variables":{"text":"x"}}'})).status);
ok('forged CSRF token is refused (403)', forged===403, String(forged));
// 7 expired/removed session while using the app -> back to sign-in
const all = await b.cookies(); await b.deleteCookie(...all.filter(c=>c.name==='JSESSIONID'));
await clickText('Provider Status'); await sleep(2500);
ok('losing the session sends the user back to sign-in', p.url().endsWith('/login'), p.url());
// 8 login again, logout, protected after logout
await p.type('#username','demo'); await p.type('#password',PW); await p.click('button[type=submit]'); await p.waitForFunction(()=>location.pathname!=='/login',{timeout:15000}); await sleep(1000);
ok('can sign in again', !p.url().endsWith('/login'));
await clickText('Sign out'); await sleep(2000);
ok('logout returns to the sign-in page', p.url().endsWith('/login'), p.url());
await nav('/provider'); ok('protected route not accessible after logout', p.url().endsWith('/login'));
const after = await p.evaluate(async()=> (await fetch('/api/v1/ai/usage')).status);
ok('API refuses the logged-out browser (401)', after===401, String(after));
ok('browser only talked to https://localhost', [...origins].every(o=>o==='https://localhost'), [...origins].join(','));
console.log('API calls seen by the browser:\n  '+[...new Set(calls)].join('\n  '));
const failed = results.filter(r=>r[0]==='FAIL').length;
console.log(failed+' failed of '+results.length);
process.exitCode = failed ? 1 : 0;
await b.close();
