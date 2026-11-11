import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import vm from 'node:vm';
const dir=new URL('../../app/src/main/assets/core/',import.meta.url);
function runtime(){const CoreData={};for(const file of fs.readdirSync(dir).filter(f=>f.endsWith('.json')))CoreData[file]=JSON.parse(fs.readFileSync(new URL(file,dir)));
 const ctx=vm.createContext({CoreData});for(const file of ['main.js',...fs.readdirSync(dir).filter(f=>f.endsWith('.js')&&f!=='main.js').sort()])vm.runInContext(fs.readFileSync(new URL(file,dir),'utf8'),ctx);
 return (name,args={})=>JSON.parse(ctx.dispatch(name,JSON.stringify(args)));}
const session={connectionId:'one',modern:true,name:'Test instance'};
const state={connections:[],presets:[],extensions:[]};
const open=(call,page,extra={})=>call('monitor.open',{data:{page},session,state,language:'zh',...extra});
function walk(tree){return [tree,...(tree.children||[]).flatMap(walk)];}
function text(command){return walk(command.tree||command).map(n=>n.text||'').join('\n');}
test('status interval is per connection, validates bounds and preserves encrypted state',()=>{
 const call=runtime(),saved={...state,monitor:{one:{interval:30},two:{interval:60}}};
 const page=open(call,'status',{state:saved});assert.equal(page.commands.at(-1).intervalMs,30000);
 for(const value of [-1,1,4,5.5,3601,'no'])assert.throws(()=>call('monitor.interval',{value}));
 assert.equal(call('monitor.interval',{value:'0'}),0);
 const change=call('monitor.statusAction',{data:{action:'save',fields:{interval:'60'}},state:saved});
 assert.deepEqual(change.state.monitor,{one:{interval:60},two:{interval:60}});assert.equal(saved.monitor.one.interval,30);
 assert.equal(change.next.intervalMs,60000);
});
test('automatic status updates replace only the status slot and retain last data on failures',()=>{
 const call=runtime();open(call,'status');
 const update=call('monitor.status',{data:{kind:'data',data:{cpu_percent:20,thread_count:3}},language:'zh'});
 assert.equal(update.kind,'render');assert.equal(update.slot,'stats');assert.ok(walk(update.tree).some(n=>n.type==='progress'));
 const failure=call('monitor.status',{data:{kind:'error',status:503,retrying:true,message:'offline'},language:'zh'});
 assert.ok(text(failure).includes('自动重连'));assert.ok(walk(failure.tree).some(n=>n.type==='progress'));
});
test('live logs replay with a cursor, deduplicate history/live overlap, pause and filter locally',()=>{
 const call=runtime();const page=open(call,'logs');assert.deepEqual(page.commands.at(-1).paths,['/api/v1/logs/history','/api/log-history']);
 const history=call('monitor.logHistory',{data:{kind:'data',data:{logs:[{time:10,level:'ERROR',data:'original 中文\nline two'}]}},language:'zh'});
 assert.equal(history.commands.at(-1).lastEventId,'10');assert.equal(history.commands.at(-1).mode,'sse');
 let update=call('monitor.logs',{data:{kind:'frames',frames:[{id:'10',data:JSON.stringify({type:'log',time:10,level:'ERROR',data:'original 中文\nline two'})}]},language:'en'});
 assert.equal(text(update).split('original 中文').length-1,1);
 assert.ok(walk(update.tree).some(n=>n.literal&&n.text==='original 中文\nline two'));
 const paused=call('monitor.logAction',{data:{action:'pause'},language:'zh'});assert.equal(paused.commands[0].kind,'cancelWatch');
 const resume=call('monitor.logAction',{data:{action:'pause'},language:'zh'});assert.equal(resume.commands[0].lastEventId,'10');
 update=call('monitor.logAction',{data:{action:'filter',fields:{logQuery:'absent',logLevel:'ERROR'}},language:'zh'});assert.ok(!text(update).includes('original 中文'));
});
test('log retention is bounded and switching instances removes previous records',()=>{
 const call=runtime();open(call,'logs');
 const frames=Array.from({length:600},(_,i)=>({id:String(i),data:JSON.stringify({time:i,level:'INFO',data:'record-'+i})}));
 const result=call('monitor.logs',{data:{kind:'frames',frames},language:'en'});assert.ok(text(result).includes('record-599'));assert.ok(!text(result).includes('record-300\n'));
 open(call,'logs',{session:{...session,connectionId:'two'}});
 const next=call('monitor.logs',{data:{kind:'connected'},language:'en'});assert.ok(!text(next).includes('record-599'));
});
test('market normalizes keyed/list catalogues and rejects non-HTTP repository actions',()=>{
 const call=runtime();const entries=call('monitor.marketNormalize',{data:{$meta:{},'owner/tool':{name:'tool',desc:{zh:'中文介绍',en:'English description'},repo:'https://github.com/owner/tool',stars:50},bad:{name:'bad',repo:'javascript:alert(1)'}},language:'zh'});
 assert.equal(entries.length,2);assert.equal(entries[0].id,'owner/tool');assert.equal(entries[0].desc,'中文介绍');assert.equal(entries[1].repo,'');
 assert.equal(call('monitor.marketNormalize',{data:[{name:'x',repo:'https://example.com/x'}],language:'en'})[0].name,'x');
});
test('market search/pagination and install use authoritative context with one confirmed mutation',()=>{
 const call=runtime();open(call,'market');
 const data={'owner/tool':{name:'tool',author:'owner',desc:'Useful plugin',repo:'https://github.com/owner/tool',version:'1.0'}};
 call('monitor.marketData',{data:{kind:'data',data},language:'en'});
 const detail=call('monitor.marketAction',{data:{action:'detail',id:'owner/tool'},language:'en'});assert.ok(text(detail).includes('Useful plugin'));
 const install=call('monitor.marketAction',{data:{action:'install',id:'owner/tool',fields:{proxy:''}},language:'en'});
 assert.equal(install.kind,'request');assert.equal(install.method,'POST');assert.equal(install.path,'/api/v1/plugins/install/github');assert.equal(install.body.repository,'https://github.com/owner/tool');assert.equal(install.body.ignore_version_check,false);assert.ok(install.confirmMessage.includes('Test instance'));assert.ok(!install.legacy);
 call('monitor.marketInstalled',{data:{kind:'data',data:{plugins:[{name:'tool',repo:'https://github.com/owner/tool.git'}]}}});
 assert.equal(call('monitor.marketAction',{data:{action:'install',id:'owner/tool',fields:{}},language:'en'}).kind,'none');
 open(call,'market',{session:{...session,modern:false}});call('monitor.marketData',{data:{kind:'data',data},language:'zh'});
 const old=call('monitor.marketAction',{data:{action:'install',id:'owner/tool',fields:{}},language:'zh'});assert.equal(old.path,'/api/plugin/install');assert.equal(old.body.url,'https://github.com/owner/tool');
});
test('permission failures have actionable explanations and never start a stream',()=>{
 const call=runtime();open(call,'logs');const result=call('monitor.logHistory',{data:{kind:'error',status:403},language:'zh'});assert.equal(result.kind,'render');assert.ok(text(result).includes('system'));
 assert.ok(call('monitor.error',{data:{status:404},language:'en'}).includes('version'));
});
test('history exact-number wrappers preserve the resume cursor',()=>{
 const call=runtime();open(call,'logs');const r=call('monitor.logHistory',{data:{kind:'data',data:{logs:[{time:{__astrbot_exact_number__:'1794345678.123456'},data:'precision',level:'INFO'}]}},language:'zh'});
 assert.equal(r.commands.at(-1).lastEventId,'1794345678.123456');assert.ok(text(r.commands[0]).includes('precision'));
});
