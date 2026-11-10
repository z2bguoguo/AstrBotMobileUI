/* UI ABI 1: native widgets, script-owned trees, layout and event descriptors. */
var UI = (function(){
  'use strict';
  function merge(a,b){Object.keys(b||{}).forEach(function(k){a[k]=b[k];});return a;}
  function column(children,style){return merge({type:'column',children:children||[]},style);}
  function row(children,style){return merge({type:'row',gravity:'centerVertical',children:children||[]},style);}
  function text(value,size,color,style){return merge({type:'text',text:String(value===undefined?'':value),size:size||14,color:color||'ink',padding:[0,5,0,5]},style);}
  function button(title,action,primary){return {type:'button',text:title,action:action,size:14,color:primary?'onAccent':'accent',background:primary?'accent':'surface',bold:!!primary,ripple:true,radius:24,height:48,margin:[0,8,0,0]};}
  function group(children){return column(children,{background:'surface',radius:26,padding:[0,4,0,4],margin:[0,0,0,10]});}
  function nav(d){
    var children=[];
    if(d.icon)children.push(text(d.icon,20,'accent',{width:40,height:40,gravity:'center',background:'surface',radius:16}));
    var labels=[text(d.title,16,'ink',{bold:true})];if(d.detail)labels.push(text(d.detail,12,'muted'));
    children.push(column(labels,{width:0,weight:1,padding:[d.icon?13:0,0,12,0]}));
    if(d.active)children.push(text('›',25,'muted',{width:'wrap'}));
    return row(children,{padding:[16,15,16,15],minHeight:70,action:d.active?(d.action||{id:'activate'}):undefined,atomic:true,background:'transparent',ripple:!!d.active,radius:20,description:d.title+(d.detail?', '+d.detail:''),tooltip:d.detail||d.title});
  }
  function screen(d){
    var header=[];
    if(d.back)header.push(text('‹  返回',15,'ink',{action:{id:'back'},background:'surface',radius:24,padding:[16,8,16,8]}));
    header.push(text(d.title,28,'ink',{bold:true}));header.push(text(d.hint||'',13,'muted',{id:'subtitle'}));
    return column([column(header,{id:'toolbar',padding:[22,14,22,8]}),{type:'scroll',height:0,weight:1,children:[column([],{id:'content',padding:[18,8,18,32]})]}],{id:'page',background:'gradient',height:'fill'});
  }
  function status(d,env){
    var sections=Core.invoke('status.sections',{data:d.data});
    return column(sections.map(function(s){
      var children=[text(s.title,18,'ink',{bold:true})];
      var rank={'cpu_percent':0,'memory.process':1,'memory.system':2,'memory.process_share':3,'thread_count':4};
      s.metrics.sort(function(x,y){var a=x.path.join('.'),b=y.path.join('.');return (rank[a]===undefined?100:rank[a])-(rank[b]===undefined?100:rank[b]);});
      s.metrics.forEach(function(m){
        var key=m.path.join('.'),leaf=m.path[m.path.length-1];
        var title=m.label||(d.names||{})[key]||(d.names||{})[leaf]||leaf.replace(/_/g,' ');
        var formatted={kind:m.kind,value:m.value,pattern:env.language==='zh'?'yyyy-MM-dd HH:mm:ss':'yyyy-MM-dd HH:mm:ss'};
        children.push(row([text(title,14,'muted',{width:0,weight:1,maxLines:2}),{type:'text',format:formatted,size:16,color:'ink',gravity:'end',bold:true,selectable:true,width:'wrap'}],{minHeight:48,tooltip:key+(m.hint?'\n'+m.hint:'')}));
        if(m.kind==='percent'&&typeof m.value==='number'&&isFinite(m.value))children.push({type:'progress',max:1000,value:Math.round(Math.max(0,Math.min(100,m.value))*10),color:m.value>=90?'#cb6572':m.value>=75?'#c69048':'accent',height:8,margin:[0,0,0,12],description:title+' '+m.value.toFixed(1)+'%'});
        if(m.hint)children.push(text(m.hint,11,'muted'));
      });
      return merge(group(children),{padding:[16,12,16,14]});
    }));
  }
  function fields(d){
    return column(d.items.map(function(item){
      var help=button('ⓘ',{kind:'help',path:item.path},false);merge(help,{width:44,height:48,margin:[0,0,0,0],background:'transparent'});
      var children=[];
      if(item.boolean){
        children.push(row([help,{type:'switch',text:item.title,checked:item.value,enabled:!item.locked,action:item.locked?undefined:{kind:'toggle',path:item.path},color:'ink',size:16,width:0,weight:1,padding:[16,12,8,12],minHeight:60}]));
      }else {
        var entry=nav({title:item.title,detail:item.summary,icon:item.icon,active:!item.locked,action:{kind:'edit',path:item.path}});entry.width=0;entry.weight=1;
        children.push(row([help,entry]));
      }
      if(item.hint)children.push(text(item.hint,12,'muted',{padding:[16,0,16,12]}));
      if(d.array)children.push(button('删除此项',{kind:'delete',path:item.path},false));
      var card=group(children);if(!item.locked)card.action={kind:item.boolean?'toggleCard':'edit',path:item.path};return card;
    }));
  }
  function field(d,env){
    var path=d.path||[],k=path[path.length-1]||'',labels=d.labels||(typeof CoreData!=='undefined'&&CoreData['fields.json']&&CoreData['fields.json'][env.language])||{},meta=d.meta||{};
    var names=labels._names||{},full=path.filter(function(k){return !/^[0-9]+$/.test(k);}).join('.');
    function translated(v){return typeof v==='string'?(labels[v]||(/\.(description|hint|labels)$/.test(v)?'':v)):v;}
    var fallback=names[full]||names[k]||d.fallback||k.replace(/_/g,' '),desc=translated(meta.description);
    var title=typeof desc==='string'&&desc.trim()?desc:fallback;
    if(env.language==='zh'&&!/[一-鿿]/.test(title)&&/[一-鿿]/.test(fallback))title=fallback;
    if(env.language==='en'&&/[一-鿿]/.test(title)&&!/[一-鿿]/.test(fallback))title=fallback;
    if(/^[0-9]+$/.test(k))title=env.language==='zh'?'第 '+(Number(k)+1)+' 项':'Item '+(Number(k)+1);
    var value=d.value;if(value&&value.__astrbot_exact_number__)value=value.__astrbot_exact_number__;var secret=meta.obvious_hint||/password|passwd|secret|token|api.?key|authorization/i.test(k);
    var summary=value===null?'未设置':typeof value==='object'?(Array.isArray(value)?value.length+' 项':Object.keys(value).length+' 项设置'):secret&&String(value)?'••••••••':String(value===undefined?'':value).replace(/\n/g,' ');
    if(!summary)summary='未填写';if(summary.length>64)summary=summary.slice(0,64)+'…';
    return {title:title,hint:translated(meta.mobile_summary||meta.hint)||'',summary:summary};
  }
  function render(name,a){var d=a.data||{};
    if(name==='label')return text(d.text,d.size,d.color);
    if(name==='column')return column([]);
    if(name==='screen')return screen(d);
    if(name==='button')return button(d.title,{id:'activate'},d.primary);
    if(name==='group')return group([]);
    if(name==='card'){var children=[];if(d.title)children.push(text(d.title,18,'ink',{bold:true}));if(d.desc)children.push(text(d.desc,13,'muted'));return column(children,{background:'surface',radius:26,padding:[18,15,18,15],elevation:1,margin:[0,0,0,12]});}
    if(name==='nav')return nav(d);
    if(name==='textAction')return text(d.title,13,'accent',{gravity:'center',padding:[12,12,12,12],minHeight:44,action:{id:'activate'}});
    if(name==='input')return column([text(d.hint,13,'muted',{id:'caption'}),{type:'input',id:'input',value:d.value,secret:d.secret,multiline:d.multiline}]);
    if(name==='status')return status(d,a);
    if(name==='fields')return fields(d);
    if(name==='field')return field(d,a);
    if(name==='bottomNav'){
      var titles=d.connected?['总览','管理','工具']:['连接','预设','设置'];
      return row(titles.map(function(title,i){return text(title,14,d.current===title?'accent':'muted',{gravity:'center',padding:[8,14,8,14],width:0,weight:1,background:d.current===title?'surface':'transparent',radius:22,action:{index:i}});}),{padding:[12,6,12,10],background:'surface',radius:24});
    }
    throw new Error('Unknown UI template '+name);
  }
  return {render:render,nav:nav,text:text,column:column,row:row,button:button,group:group};
}());
['label','column','screen','button','group','card','nav','textAction','input','status','fields','field','bottomNav'].forEach(function(name){CoreModules['ui.'+name]=function(a){return UI.render(name,a);};});
(function(){
  function control(id,title,value,secret){return UI.column([UI.text(title,13,'muted'),{type:'input',id:id,value:value||'',secret:!!secret}]);}
  function card(title,children){return UI.column([UI.text(title,18,'ink',{bold:true})].concat(children),{background:'surface',radius:26,padding:[18,15,18,15],margin:[0,0,0,12]});}
  CoreModules['ui.home']=function(a){var d=a.data;return UI.column([
    card('随时连接，轻松管理',[UI.text('在不同实例间切换，让配置、提示词和工具井井有条。',13,'muted'),UI.button('＋ 添加连接',{kind:'newConnection'},true)]),
    card('我的连接',[control('filter','搜索名称、分组或地址',d.filter)]),UI.column([],{id:'connections'})]);};
  CoreModules['ui.connections']=function(a){
    var data=a.data,entries=(data.connections||[]).slice(),out=[],last=null,needle=(data.filter||'').toLowerCase();
    entries.sort(function(x,y){return String(x.group||'').localeCompare(String(y.group||''))||String(x.name||'').localeCompare(String(y.name||''));});
    entries.forEach(function(c){if((String(c.name)+String(c.group)+String(c.url)).toLowerCase().indexOf(needle)<0)return;
      if(last!==c.group){out.push(UI.text(c.group,17,'muted'));last=c.group;}
      out.push(UI.group([UI.nav({title:c.name,detail:c.url,icon:'◉',active:true,action:{kind:'connect',id:c.id}}),UI.text('连接设置',13,'accent',{action:{kind:'connection',id:c.id},gravity:'center',padding:[12,12,12,12],minHeight:44})]));
    });if(!entries.length)out.push(UI.text('还没有连接。添加你的第一台 AstrBot。',15,'muted'));return UI.column(out);
  };
  CoreModules['ui.connection']=function(a){var d=a.data,c=d.connection,account=!c.apiKey;
    var children=[control('name','名称',c.name),control('group','分组',c.group),control('url','服务地址',c.url),
      {type:'choice',id:'auth',options:[{label:'账户密码',value:'account'},{label:'API Key',value:'key'}],selected:account?0:1,action:{kind:'auth'}},
      UI.column([control('username','WebUI 用户名',c.username),control('password','WebUI 密码',c.password,true)],{id:'accountFields',visible:account}),
      UI.column([control('apiKey','API Key（可选，填写后优先使用）',c.apiKey,true)],{id:'keyFields',visible:!account}),
      {type:'choice',id:'passwordMode',options:[{label:'原文密码（新版默认）',value:'plain'},{label:'MD5 密码（部分旧版）',value:'md5'}],selected:c.passwordMode==='md5'?1:0},
      UI.text('HTTP 会明文传输凭据，建议使用 HTTPS 或可信局域网。API Key 权限不足时可改用 WebUI 账户。',13,'muted'),UI.button('保存连接',{kind:'save'},true)];
    if(!d.isNew){children.push(UI.button('复制已保存的连接',{kind:'duplicate'},false));children.push(UI.button('删除连接',{kind:'delete'},false));}return card('基本信息',children);
  };
  CoreModules['ui.connectionAuth']=function(a){return {accountFields:{visible:a.value==='account'},keyFields:{visible:a.value==='key'}};};
  CoreModules['ui.connectionValue']=function(a){var c=JSON.parse(JSON.stringify(a.connection)),f=a.fields;['name','group','url','username','password','passwordMode'].forEach(function(k){if(k in f)c[k]=f[k];});c.apiKey=f.auth==='key'?f.apiKey||'':'';return c;};
  CoreModules['ui.connectionCommit']=function(a){var state=JSON.parse(JSON.stringify(a.state)),list=state.connections||[],c=a.connection;
    if(a.operation==='delete')list=list.filter(function(x){return x.id!==c.id;});
    else if(a.operation==='duplicate'){c=JSON.parse(JSON.stringify(c));c.id=a.newId;c.name+=' 副本';list.push(c);}
    else {var found=false;list=list.map(function(x){if(x.id!==c.id)return x;found=true;return c;});if(!found)list.push(c);}
    state.connections=list;return state;
  };
  var menu={
    workspace:[['配置与运行',[['通用设置','基础配置、管理员与运行选项','⚙',{kind:'settings',id:'system'}],['配置文件','不同场景使用不同配置','▤',{kind:'profiles'}]]],
      ['对话与智能',[['人格与提示词','编辑系统提示词与开场对话','✦',{kind:'resource',resource:'PERSONAS'}],['模型服务','查看与编辑模型连接','◈',{kind:'resource',resource:'PROVIDERS'}],['MCP 服务','连接地址、启动参数与环境变量','⌘',{kind:'resource',resource:'MCP'}]]],
      ['平台与扩展',[['机器人平台','编辑平台连接与运行设置','◉',{kind:'resource',resource:'BOTS'}],['插件管理','启停插件、配置与自定义页面','◇',{kind:'resource',resource:'PLUGINS'}]]]],
    advanced:[['预设与配置传递',[['预设与配置传递','保存常用设置，在实例间复用','≡',{kind:'presets'}],['客户端插件','管理客户端扩展','◇',{kind:'extensions'}],['插件自定义页面','打开 HTML 配置页面','▣',{kind:'pluginEntry'}]]],['高级工具',[['接口浏览器','搜索全部接口操作','⌘',{kind:'operations'}],['自定义请求','手动指定接口和参数','↗',{kind:'manual'}]]]]
  };
  ['workspace','advanced'].forEach(function(name){CoreModules['ui.'+name]=function(){return UI.column(menu[name].map(function(section){return card(section[0],section[1].map(function(r){return UI.nav({title:r[0],detail:r[1],icon:r[2],active:true,action:r[3]});}));}));};});
}());
CoreModules['ui.theme']=function(a){
  var dark=a.mode==='dark'||a.mode==='amoled'||a.mode==='system'&&a.systemDark,accent=a.accent;
  function mix(x,y,t){var r=Math.round(((x>>>16)&255)*(1-t)+((y>>>16)&255)*t),g=Math.round(((x>>>8)&255)*(1-t)+((y>>>8)&255)*t),b=Math.round((x&255)*(1-t)+(y&255)*t);return (0xff000000|(r<<16)|(g<<8)|b);}
  var l=(((accent>>>16)&255)*299+((accent>>>8)&255)*587+(accent&255)*114)/1000;
  return {dark:dark,accent:accent,ink:dark?0xffedf1fa:0xff252d3e,muted:dark?0xffb4bfd2:0xff667086,surface:dark?0xff232b3b:0xeeffffff,onAccent:l>170?0xff172032:0xffffffff,gradient:a.mode==='amoled'?[0xff000000,0xff000000,0xff000000]:dark?[0xff111827,0xff182132,0xff201e31]:[mix(accent,0xffffffff,.86),0xfff1f5fd,0xffeeeaf6]};
};
CoreModules['ui.preferences']=function(a){var d=a.data;
  function selector(title,detail,key,options,current,icon){return UI.nav({title:title,detail:detail,icon:icon,active:true,action:{kind:'choice',title:title,key:key,options:options,selected:options.map(function(o){return o.value;}).indexOf(current)}});}
  return UI.column([
    selector('Language / 语言',d.language==='zh'?'简体中文':'English','language',[{label:'跟随系统 / System',value:'system'},{label:'简体中文',value:'zh'},{label:'English',value:'en'}],d.languagePreference,'A'),
    selector('Theme / 主题',d.theme,'theme',[{label:'跟随系统 / System',value:'system'},{label:'浅色 / Light',value:'light'},{label:'深色 / Dark',value:'dark'},{label:'AMOLED / 纯黑',value:'amoled'}],d.theme,'◐'),
    UI.nav({title:'Accent / 主题色',detail:d.accent,icon:'●',active:true,action:{kind:'color',title:'Accent / 主题色',palette:['#526DA9','#8670B7','#3B8574','#B76683','#C17B3F']}})
  ]);
};
CoreModules['ui.color']=function(a){var d=a.data;return UI.column([{type:'input',id:'accent',value:d.value},UI.row(d.palette.map(function(hex){return UI.column([UI.text('',14,'transparent',{background:hex,width:32,height:32,radius:16})],{width:48,height:48,padding:[8,8,8,8],description:hex,action:{value:hex},atomic:true});}))],{padding:[20,8,20,8]});};
CoreModules['ui.response']=function(a){return {kind:'screen',title:'服务器结果',tree:UI.column([UI.text(JSON.stringify(Core.invoke('helpers.redact',{value:a.data}),null,2),13,'ink',{monospace:true,selectable:true})])};};
CoreModules['ui.editor']=function(a){var d=a.data;
  function small(title,kind){var node=UI.button(title,{kind:kind},false);node.width=0;node.weight=1;node.size=12;return node;}
  var actions=[small('查找','find'),small('替换全部','replace')];if(d.json)actions.push(small('格式化','format'));actions.push(small('撤销','undo'));
  return UI.column([UI.text(d.title,22,'ink'),UI.text('查找',13,'muted'),{type:'input',id:'search',value:''},UI.text('替换为',13,'muted'),{type:'input',id:'replacement',value:''},
    {type:'input',id:'editor',value:d.value,multiline:true,monospace:true,size:15,maxLines:100000,height:0,weight:1},UI.text('',12,'muted',{id:'count'}),UI.row(actions),UI.button('保存到当前表单',{kind:'apply'},true),UI.button('放弃修改',{kind:'cancel'},false)],{padding:[14,14,14,14],background:'surface',height:'fill'});
};
CoreModules['ui.editorCount']=function(a){var value=a.value;return a.language==='zh'?value.length+' 字符 · '+value.split('\n').length+' 行 · '+(value===a.original?'未修改':'已修改'):value.length+' characters · '+value.split('\n').length+' lines · '+(value===a.original?'Unchanged':'Modified');};
CoreModules['ui.editorAction']=function(a){var f=a.fields||{},text=f.editor||'',query=f.search||'';
  if(a.kind==='find'){var index=query?text.indexOf(query,a.cursor): -1;if(index<0&&query)index=text.indexOf(query);return {kind:'selection',start:index,end:index<0?index:index+query.length};}
  if(a.kind==='replace')return {kind:'text',text:query?text.split(query).join(f.replacement||''):text};
  if(a.kind==='undo')return {kind:'text',text:a.original};
  if(a.kind==='format')return {kind:'formatJson'};
  return {kind:a.kind==='apply'?'apply':'dismiss',text:text,changed:text!==a.original};
};
CoreModules['ui.saveState']=function(a){var count=a.count;return {enabled:count>0,alpha:count>0?1:0.45,label:a.language==='en'?(count?'Save changes ('+count+')':'No pending changes'):(count?'保存更改（'+count+'）':'没有待保存的更改')};};
CoreModules['ui.appSettings']=function(a){var d=a.data;
  function slot(title,id,children){return UI.column([UI.text(title,18,'ink',{bold:true})].concat(children||[]),{id:id,background:'surface',radius:26,padding:[18,15,18,15],margin:[0,0,0,12]});}
  return UI.column([
    slot('Appearance / 外观','appearance'),slot('输入体验','inputPreferences',[{type:'switch',text:'输入辅助与自动补全',checked:d.autofill,color:'ink',size:16,minHeight:60,action:{kind:'autofill'}}]),
    UI.group([UI.nav({title:'预设库',detail:'复用配置与长文本',icon:'≡',active:true,action:{kind:'presets'}}),UI.nav({title:'客户端插件',detail:'管理扩展操作',icon:'◇',active:true,action:{kind:'extensions'}})]),
    slot('应用更新','updates'),slot('关于与诊断','about',[UI.nav({title:'截图与录屏',detail:'允许，用于反馈与排查问题',icon:'◎',active:false}),UI.nav({title:'使用指南',detail:'连接、配置与插件说明',icon:'?',active:true,action:{kind:'openUrl',url:'https://z2bguoguo.github.io/AstrBotMobileUI/'}})])
  ]);
};
/* Shared UI defaults are also core data: style fixes never belong in the APK bridge. */
(function(){
  function prepare(node){
    if(!node||typeof node!=='object')return node;
    if(node.type==='button'&&!node.gravity)node.gravity='center';
    if(node.type==='input'&&node.id!=='editor'){
      if(!node.background)node.background='surface';
      if(!node.padding)node.padding=[12,12,12,12];
      if(node.radius===undefined)node.radius=18;
      if(node.size===undefined)node.size=15;
    }
    (node.children||[]).forEach(prepare);return node;
  }
  Object.keys(CoreModules).forEach(function(name){if(name.indexOf('ui.')!==0)return;var original=CoreModules[name];CoreModules[name]=function(a){var result=original(a);return result&&result.type?prepare(result):result;};});
  UI.prepare=prepare;
}());
