import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import vm from 'node:vm';
const context = vm.createContext({});
vm.runInContext(fs.readFileSync(new URL('../../app/src/main/assets/core/main.js', import.meta.url),'utf8'),context);
const call=(name,args)=>JSON.parse(context.dispatch(name,JSON.stringify(args)));
test('script core validates ABI and defines modern and legacy plugin payloads',()=>{
 assert.equal(call('ping',{}).abi,1);
 const modern=call('management.plan',{op:'save',resource:'PLUGINS',id:'p/a',config:{enabled:false},modern:true});
 assert.equal(modern.path,'/api/v1/plugins/p%2Fa/config');
 assert.deepEqual(modern.body,{config:{enabled:false}});
 const old=call('management.plan',{op:'save',resource:'PLUGINS',id:'p/a',config:{enabled:false},modern:false});
 assert.equal(old.path,'/api/config/plugin/update?plugin_name=p%2Fa');assert.deepEqual(old.body,{enabled:false});
});
test('paths retain zero and escaped queries and reject traversal',()=>{
 assert.equal(call('endpoint.resolve',{path:'/api/v1/p/{id}',values:{id:0},query:{q:['a b','x/y']}}),'/api/v1/p/0?q=a%20b&q=x%2Fy');
 assert.throws(()=>call('endpoint.resolve',{path:'/api/p/{id}',values:{id:'..'},query:{}}));
});
test('redaction and suggestions never return secrets',()=>{
 assert.deepEqual(call('helpers.redact',{value:{api_key:'private',nested:{cookie:'private',name:'bot'}}}),{api_key:'',nested:{cookie:'',name:'bot'}});
 assert.deepEqual(call('helpers.suggestions',{value:{token:{id:'leak'},safe:{name:'bot'}},depth:0}),['bot']);
});
test('nested draft diffs and metadata preserve original shape',()=>{
 assert.deepEqual(call('form.diff',{before:{a:{x:1},future:true},after:{a:{x:2},future:true}}),['a.x']);
 assert.deepEqual(call('form.default',{meta:{type:'object',items:{on:{type:'bool'},args:{type:'list'}}}}),{on:false,args:[]});
});
test('bilingual labels cover all bundled operation summaries',()=>{
 const locales=JSON.parse(fs.readFileSync(new URL('../../app/src/main/assets/core/locales.json',import.meta.url)));
 const spec=JSON.parse(fs.readFileSync(new URL('../../app/src/main/assets/openapi.json',import.meta.url)));
 assert.equal(locales.en['保存'],'Save');assert.equal(locales.zh.Save,'保存');
 for(const methods of Object.values(spec.paths))for(const op of Object.values(methods))if(op&&op.summary)assert.ok(locales.zh[op.summary],op.summary);
});
test('status descriptors combine runtime and preserve documented MiB memory semantics',()=>{
 const sections=call('status.sections',{data:{running:{hours:0,minutes:10,seconds:52},memory:{process:660,system:1699},cpu_percent:3.0,thread_count:22,start_time:1794155493}});
 const metrics=sections.flatMap(s=>s.metrics);
 assert.equal(metrics.find(m=>m.kind==='duration').value,652);
 assert.equal(metrics.find(m=>m.path.join('.')==='memory.process').value,660*1048576);
 assert.equal(metrics.find(m=>m.path.join('.')==='memory.system').value,1699*1048576);
 assert.equal(metrics.find(m=>m.path[0]==='cpu_percent').kind,'percent');
 assert.equal(metrics.find(m=>m.path[0]==='start_time').kind,'timestamp');
 const share=metrics.find(m=>m.path.join('.')==='memory.process_share');
 assert.ok(Math.abs(share.value-660/1699*100)<0.001);
 assert.equal(share.hint,'进程内存 / 系统总内存');
 assert.ok(!metrics.some(m=>m.path.at(-1)==='seconds'));
});
test('status descriptors retain zero, explicit ratios, bytes and nested percentage context',()=>{
 const sections=call('status.sections',{data:{cpu_percent:0,disk:{used_percent:52.5,free_bytes:1024},load_ratio:0.4,uptime_seconds:0}});
 const metrics=sections.flatMap(s=>s.metrics);
 assert.equal(metrics.find(m=>m.path[0]==='cpu_percent').value,0);
 assert.equal(metrics.find(m=>m.path.join('.')==='disk.used_percent').kind,'percent');
 assert.equal(metrics.find(m=>m.path.join('.')==='disk.free_bytes').kind,'bytes');
 assert.equal(metrics.find(m=>m.path[0]==='load_ratio').value,40);
 assert.equal(metrics.find(m=>m.kind==='duration').value,0);
});
test('invalid status data never produces a fabricated progress percentage and excludes secrets',()=>{
 const metrics=call('status.sections',{data:{cpu_percent:null,running:{hours:-1,minutes:10},memory:{process:2,system:0},password:'private',extra:{api_key:'private'}}}).flatMap(s=>s.metrics);
 assert.ok(!metrics.some(m=>m.kind==='percent'));assert.ok(!metrics.some(m=>m.kind==='duration'));
 assert.ok(!JSON.stringify(metrics).includes('private'));
});
test('configuration names cover untranslated legacy root groups in both languages',()=>{
 const fields=JSON.parse(fs.readFileSync(new URL('../../app/src/main/assets/core/fields.json',import.meta.url)));
 assert.equal(fields.zh._names.config_version,'配置格式版本');
 assert.equal(fields.zh._names.platform_settings,'消息平台设置');
 assert.equal(fields.zh._names.provider_sources,'模型服务来源');
 assert.equal(fields.en._names.platform_settings,'Platform settings');
 assert.equal(fields.zh._names['memory.process'],'进程内存');
});
