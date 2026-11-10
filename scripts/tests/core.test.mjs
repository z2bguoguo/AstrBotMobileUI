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
