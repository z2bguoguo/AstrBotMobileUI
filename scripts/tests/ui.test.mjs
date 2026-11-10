import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import vm from 'node:vm';
function runtime(){const context=vm.createContext({});for(const file of ['main.js','ui.js'])vm.runInContext(fs.readFileSync(new URL('../../app/src/main/assets/core/'+file,import.meta.url),'utf8'),context);return context;}
const context=runtime();const call=(name,args={})=>JSON.parse(context.dispatch(name,JSON.stringify(args)));
function walk(node){return [node,...(node.children||[]).flatMap(walk)];}
test('row touch policy and every decorative descendant are declared by scripts',()=>{
 const tree=call('ui.nav',{data:{title:'Language',detail:'English',icon:'A',active:true}});
 assert.equal(tree.type,'row');assert.equal(tree.atomic,true);assert.equal(tree.action.id,'activate');
 assert.ok(!tree.children.some(c=>c.action));assert.equal(tree.minHeight,70);
});
test('dashboard tree contains native percentage bars and script-selected timestamp format',()=>{
 const tree=call('ui.status',{data:{data:{cpu_percent:3,memory:{process:660,system:1699},running:{minutes:10,seconds:52},start_time:1794155493}},language:'zh'});
 const nodes=walk(tree),bars=nodes.filter(n=>n.type==='progress');assert.equal(bars.length,2);assert.ok(bars.some(p=>p.value===30));
 assert.equal(nodes.find(n=>n.format?.kind==='timestamp').format.pattern,'yyyy-MM-dd HH:mm:ss');
});
test('settings rows keep independent help and readonly controls',()=>{
 const tree=call('ui.fields',{data:{array:false,items:[{title:'Enabled',path:['enabled'],boolean:true,value:true,locked:true}]}});
 const nodes=walk(tree),toggle=nodes.find(n=>n.type==='switch');assert.equal(toggle.enabled,false);assert.equal(toggle.action,undefined);
 assert.ok(nodes.some(n=>n.action?.kind==='help'));assert.ok(!nodes.some(n=>n.action?.kind==='toggleCard'));
});
test('connection editor preserves values and selects only chosen authentication',()=>{
 const original={id:'a',name:'server',username:'user',password:'unchanged secret',apiKey:'old key',passwordMode:'plain'};
 const next=call('ui.connectionValue',{connection:original,fields:{name:'new',auth:'account',apiKey:'ignored',password:'unchanged secret',passwordMode:'md5'}});
 assert.equal(next.apiKey,'');assert.equal(next.password,'unchanged secret');assert.equal(next.passwordMode,'md5');assert.equal(original.name,'server');
});
test('connection commits copy state and preserve other credentials during group operations',()=>{
 const state={connections:[{id:'a',name:'one',password:'secret',group:'g'}],presets:[{name:'keep'}]};
 const copy=call('ui.connectionCommit',{state,connection:state.connections[0],operation:'duplicate',newId:'b'});
 assert.equal(copy.connections.length,2);assert.equal(copy.connections[1].id,'b');assert.equal(copy.connections[1].password,'secret');assert.equal(state.connections.length,1);
 const removed=call('ui.connectionCommit',{state:copy,connection:{id:'a'},operation:'delete'});assert.equal(removed.connections[0].id,'b');assert.deepEqual(removed.presets,state.presets);
});
test('new appearance modes are script data over the same renderer ABI',()=>{
 const preferences=call('ui.preferences',{data:{language:'zh',languagePreference:'zh',theme:'amoled',accent:'#526DA9'}});
 assert.ok(walk(preferences).some(n=>n.action?.options?.some(o=>o.value==='amoled')));
 const theme=call('ui.theme',{mode:'amoled',accent:0xff526da9,systemDark:false});assert.equal(theme.dark,true);assert.deepEqual(theme.gradient,[0xff000000,0xff000000,0xff000000]);
});
test('a replaced UI module changes layout without touching native bridge code',()=>{
 const changed=runtime();vm.runInContext("CoreModules['ui.nav']=function(){return {type:'row',atomic:true,minHeight:88,children:[{type:'text',text:'updated'}]};};",changed);
 const node=JSON.parse(changed.dispatch('ui.nav','{}'));assert.equal(node.minHeight,88);assert.equal(node.children[0].text,'updated');
});
test('long-text actions preserve original data and leave exact JSON parsing to native primitive',()=>{
 const fields={editor:'第一行\n第二行 第一行',search:'第一行',replacement:'替换'};
 assert.equal(call('ui.editorAction',{kind:'replace',fields}).text,'替换\n第二行 替换');
 assert.deepEqual(call('ui.editorAction',{kind:'find',fields,cursor:1}),{kind:'selection',start:8,end:11});
 assert.equal(call('ui.editorAction',{kind:'format',fields}).kind,'formatJson');
 assert.equal(call('ui.editorAction',{kind:'apply',fields,original:'old'}).text,fields.editor);
 assert.ok(call('ui.editorCount',{value:fields.editor,original:'old',language:'zh'}).includes('2 行'));
});
