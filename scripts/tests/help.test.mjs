import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import vm from 'node:vm';
const assets=new URL('../../app/src/main/assets/core/',import.meta.url);
function runtime(){
 const CoreData={};for(const file of fs.readdirSync(assets).filter(f=>f.endsWith('.json')))CoreData[file]=JSON.parse(fs.readFileSync(new URL(file,assets)));
 const context=vm.createContext({CoreData});
 for(const file of ['main.js',...fs.readdirSync(assets).filter(f=>f.endsWith('.js')&&f!=='main.js').sort()])vm.runInContext(fs.readFileSync(new URL(file,assets),'utf8'),context);
 return (name,args={})=>JSON.parse(context.dispatch(name,JSON.stringify(args)));
}
const call=runtime();
test('every inventoried field has detailed bilingual help and an example',()=>{
 const catalogue=JSON.parse(fs.readFileSync(new URL('help.json',assets)));
 const fields=JSON.parse(fs.readFileSync(new URL('fields.json',assets)));
 const names=JSON.parse(fs.readFileSync(new URL('../../scripts/dev/field-names.json',import.meta.url)));
 for(const key of Object.keys(names))assert.ok(catalogue.entries[key],key);
 for(const [key,entry] of Object.entries(catalogue.entries)){
  assert.ok(entry.sources.length,key);assert.ok('example' in entry,key);
  for(const language of ['zh','en']){const text=fields[language][entry.hint];assert.ok(text.length>250,key);assert.ok(text.includes(language==='zh'?'填写示例':'Example'),key);assert.ok(fields[language][entry.summary],key);}
 }
});
test('metadata enrichment keeps schema and exact-number defaults untouched',()=>{
 const root={rate_limit:{items:{time:{type:'int',minimum:1,maximum:3600,default:{__astrbot_exact_number__:'9007199254740993'}}}}};
 const before=JSON.stringify(root),meta=call('form.metadata',{root,path:['rate_limit','time']});
 assert.equal(meta.type,'int');assert.equal(meta.minimum,1);assert.equal(meta.maximum,3600);assert.deepEqual(meta.default,{__astrbot_exact_number__:'9007199254740993'});assert.ok(meta.hint.startsWith('mobile_help.'));assert.equal(JSON.stringify(root),before);
});
test('lookup retains full parent context and ignores array indices',()=>{
 assert.equal(call('help.lookup',{path:['memory','system']}).hint,'mobile_help.memory.system.hint');
 assert.equal(call('help.lookup',{path:['platform','0','id']}).hint,'mobile_help.id.hint');
 assert.equal(call('help.lookup',{path:['plugin','custom_api_key']}),null);
});
test('custom and newer server hints take precedence over bundled explanations',()=>{
 const root={api_key:{type:'string',hint:'Custom plugin credential: exact prefix required.',description:'Plugin key'}};
 assert.deepEqual(call('form.metadata',{root,path:['api_key']}),root.api_key);
 assert.equal(call('form.metadata',{root:{special:{hint:'Plugin docs',type:'string'}},path:['special']}).hint,'Plugin docs');
 assert.equal(call('form.metadata',{root:{special:{type:'string',default:'keep'}},path:['special']}).hint,'mobile_help.unknown.hint');
});
test('help stays detailed while field cards use a short localized summary',()=>{
 const meta=call('form.metadata',{root:{},path:['config_version']});
 const row=call('ui.field',{data:{path:['config_version'],value:2,meta},language:'zh'});
 assert.ok(row.hint.includes('迁移'));assert.ok(!row.hint.includes('https://github.com'));
 const english=call('ui.field',{data:{path:['config_version'],value:2,meta},language:'en'});
 assert.ok(english.hint.includes('migration'));
});
test('examples retain JSON types, valid enum choices and numeric token budgets',()=>{
 const entries=JSON.parse(fs.readFileSync(new URL('help.json',assets))).entries;
 for(const [key,entry] of Object.entries(entries)){
  const example=entry.example,type=entry.type;
  if(['int','integer','float','number'].includes(type))assert.equal(typeof example,'number',key);
  if(['bool','boolean'].includes(type))assert.equal(typeof example,'boolean',key);
  if(['list','array','template_list'].includes(type))assert.ok(Array.isArray(example),key);
  if(['object','dict'].includes(type))assert.ok(example&&typeof example==='object'&&!Array.isArray(example),key);
  if(entry.options?.length)for(const value of Array.isArray(example)?example:[example])assert.ok(entry.options.includes(value),key);
 }
 assert.equal(typeof entries.max_tokens.example,'number');
 assert.deepEqual(entries.key.example,['YOUR_API_KEY']);
 assert.equal(entries['agent.runtime'].example,'astrbot');
 assert.equal(entries['provider_ltm_settings.active_reply.method'].example,'possibility_reply');
 assert.equal(entries.identifier.example,true);
});
test('a plugin reusing a built-in key with different semantics never receives the built-in explanation',()=>{
 const meta=call('form.metadata',{root:{time:{type:'int',description:'Animation frame index',default:1}},path:['time']});
 assert.equal(meta.hint,'mobile_help.unknown.hint');
 assert.equal(meta.description,'Animation frame index');assert.equal(meta.default,1);
});
