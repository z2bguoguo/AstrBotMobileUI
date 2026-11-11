/* Script-owned monitoring and marketplace. Native host only schedules bounded reads. */
(function(){
 'use strict';
 var session={},lang='zh',status={},logs={},market={};
 function tr(zh,en){return lang==='en'?en:zh;}
 function clone(x){return JSON.parse(JSON.stringify(x));}
 function text(s,size,color){return UI.text(s,size||14,color||'ink',{literal:true,selectable:true});}
 function event(handler,action,extra){var out={kind:'event',handler:handler,action:action};Object.keys(extra||{}).forEach(function(k){out[k]=extra[k];});return out;}
 function batch(commands){return {kind:'batch',commands:commands};}
 function render(slot,tree){return {kind:'render',slot:slot,tree:tree};}
 function slot(id){return UI.column([],{id:id});}
 function button(title,handler,action,extra){return UI.button(title,event(handler,action,extra),false);}
 function screen(title,hint,tree,back){return {kind:'screen',title:title,hint:hint,tree:tree,back:back||{kind:'event',handler:'monitor.back'}};}
 function paths(modern,old){return session.modern?[modern,old]:[old];}
 function watch(id,handler,extra){var out={kind:'watch',id:id,handler:handler};Object.keys(extra).forEach(function(k){out[k]=extra[k];});return out;}
 function errorMessage(e){var s=e.status;
  if(s===403)return tr('权限不足：状态与日志需要 system 授权，插件市场需要 plugins 授权。请检查当前 API Key 的范围。','Permission denied: status/logs require system scope; the marketplace requires plugins scope. Check this API key.');
  if(s===401)return tr('登录已失效，或旧接口仅支持 WebUI 账户登录。请重新连接并检查认证方式。','Authentication expired, or this legacy route requires a dashboard account. Reconnect and check authentication.');
  if(s===404||s===405)return tr('此服务器版本没有提供对应接口。','This server version does not provide the requested endpoint.');
  return (e.retrying?tr('连接中断，正在自动重连。','Connection interrupted; reconnecting automatically.'):tr('读取失败。','Read failed.'))+' '+String(e.message||'').slice(0,300);
 }
 CoreModules['monitor.error']=function(a){lang=a.language||lang;return errorMessage(a.data);};
 CoreModules['monitor.back']=function(){return {kind:'workspace'};};
 function interval(value){var number=typeof value==='string'&&!value.trim()?NaN:Number(value);if(!isFinite(number)||Math.floor(number)!==number||number!==0&&(number<5||number>3600))throw new Error(tr('刷新间隔应为 0（关闭）或 5–3600 的整数秒。','Use 0 (off) or an integer from 5 to 3600 seconds.'));return number;}
 CoreModules['monitor.interval']=function(a){return interval(a.value);};
 function statusWatch(){return watch('status','monitor.status',{resource:'overview',intervalMs:status.interval*1000});}
 function statusOpen(a){var saved=(a.state.monitor||{})[session.connectionId];status={interval:10,data:null};if(saved)try{status.interval=interval(saved.interval);}catch(ignored){}
  return batch([screen(session.name||'AstrBot',tr('运行概览','Overview'),UI.column([
   slot('stats'),text(tr('自动刷新间隔（秒）','Automatic refresh interval (seconds)'),13,'muted'),
   {type:'input',id:'interval',value:String(status.interval)},text(tr('0 为关闭；5–3600 秒。仅在此页处于前台时刷新。','0 disables; 5–3600 seconds. Refresh runs only while this page is in the foreground.'),12,'muted'),
   button(tr('保存刷新间隔','Save interval'),'monitor.statusAction','save'),button(tr('立即刷新','Refresh now'),'monitor.statusAction','refresh'),
   button(tr('查看实时日志','Live logs'),'monitor.open','',{page:'logs'}),button(tr('原始状态','Raw status'),'monitor.statusAction','raw')
  ])),statusWatch()]);
 }
 CoreModules['monitor.statusAction']=function(a){lang=a.language||lang;
  if(a.data.action==='raw')return status.data?{kind:'viewer',title:tr('原始状态','Raw status'),data:status.data}:{kind:'toast',text:tr('请先刷新状态','Refresh status first')};
  if(a.data.action==='save'){
   try{status.interval=interval(a.data.fields.interval);}catch(e){return {kind:'toast',text:e.message};}
   var state=clone(a.state);state.monitor=state.monitor||{};state.monitor[session.connectionId]={interval:status.interval};
   return {kind:'saveState',state:state,next:statusWatch()};
  }return statusWatch();
 };
 CoreModules['monitor.status']=function(a){lang=a.language||lang;var e=a.data;
  if(e.kind==='error')return render('stats',UI.column([text(errorMessage(e),13,'muted')].concat(status.data?[Core.invoke('ui.status',{data:{data:status.data,names:CoreData['fields.json'][lang]._names},language:lang})]:[])));
  status.data=e.data;return render('stats',UI.column([text(tr('已更新 · ','Updated · ')+new Date().toLocaleTimeString(),12,'muted'),Core.invoke('ui.status',{data:{data:e.data,names:CoreData['fields.json'][lang]._names},language:lang})]));
 };
 function levelLabel(level){var names={DEBUG:'调试',INFO:'信息',WARNING:'警告',ERROR:'错误',CRITICAL:'严重'};return lang==='zh'&&names[level]?names[level]+' · '+level:level;}
 function logControls(){return UI.column([button(logs.paused?tr('继续接收','Resume'):tr('暂停接收','Pause'),'monitor.logAction','pause'),button(tr('清空本地显示','Clear local view'),'monitor.logAction','clear')]);}
 function numeric(v){return Number(v&&v.__astrbot_exact_number__||v||0);}
 function addLogs(items){items.forEach(function(item){if(typeof item==='string')item={data:item};if(!item||typeof item!=='object')return;
  var message=String(item.data||item.message||item.msg||'').slice(0,4096),time=numeric(item.time);
  if(!message)return;var key=String(item.timestamp&&item.timestamp.__astrbot_exact_number__||item.timestamp||time)+'|'+String(item.level||'')+'|'+message;
  if(logs.seen[key])return;
  var record={key:key,time:time,level:String(item.level||item.severity_text||'INFO').toUpperCase(),message:message};
  logs.rows.push(record);logs.seen[key]=true;logs.bytes+=message.length;
  while(logs.rows.length>300||logs.bytes>262144){var old=logs.rows.shift();delete logs.seen[old.key];logs.bytes-=old.message.length;}
 });logs.rows.sort(function(x,y){return x.time-y.time;});}
 function logView(){var q=logs.query.toLowerCase(),matches=logs.rows.filter(function(r){return (!logs.level||r.level===logs.level)&&(!q||(r.message+' '+r.level).toLowerCase().indexOf(q)>=0);});
  var nodes=[text(logs.connection||tr('正在连接…','Connecting…'),12,'muted'),text(tr('仅保留最近 300 条、最多显示 100 条；日志不写入本机存储。','Keeps up to 300 recent records and displays the latest 100. Logs are not persisted on this device.'),12,'muted')];
  if(!matches.length)nodes.push(text(tr('暂无符合筛选条件的日志','No matching log records'),14,'muted'));
  matches.slice(-100).reverse().forEach(function(r){nodes.push(UI.group([text((r.time?new Date(r.time*1000).toLocaleString():'')+' · '+levelLabel(r.level),12,'muted'),UI.text(r.message,12,/ERROR|CRITICAL/.test(r.level)?'#d36a78':'ink',{literal:true,monospace:true,selectable:true,padding:[12,6,12,12]})]));});
  return render('logRows',UI.column(nodes));
 }
 function streamWatch(){return watch('logs','monitor.logs',{mode:'sse',paths:paths('/api/v1/logs/live','/api/live-log'),lastEventId:logs.lastId});}
 function logsOpen(){logs={rows:[],seen:{},bytes:0,query:'',level:'',paused:false,lastId:'',connection:tr('正在读取历史日志…','Reading recent logs…')};
  return batch([screen(tr('实时日志','Live logs'),tr('当前实例的服务器输出','Server output for the selected instance'),UI.column([
   text(tr('搜索日志','Search logs'),13,'muted'),{type:'input',id:'logQuery',value:'',onChange:event('monitor.logAction','filter')},
   {type:'choice',id:'logLevel',selected:0,options:[{label:tr('所有级别','All levels'),value:''}].concat(['DEBUG','INFO','WARNING','ERROR','CRITICAL'].map(function(l){return {label:levelLabel(l),value:l};})),action:event('monitor.logAction','filter')},
   slot('logControls'),slot('logRows')
  ])),render('logControls',logControls()),logView(),watch('history','monitor.logHistory',{paths:paths('/api/v1/logs/history','/api/log-history')})]);
 }
 CoreModules['monitor.logHistory']=function(a){lang=a.language||lang;var e=a.data;
  if(e.kind==='data'){
   var records=Array.isArray(e.data)?e.data:e.data&&e.data.logs||[];addLogs(records);
   records.forEach(function(r){if(numeric(r.time)>Number(logs.lastId||0))logs.lastId=String(r.time&&r.time.__astrbot_exact_number__||r.time);});
  }else if(e.status===401||e.status===403){logs.connection=errorMessage(e);return logView();}
  logs.connection=logs.paused?tr('已暂停接收','Paused'):tr('正在连接实时日志…','Connecting live logs…');
  return batch([logView()].concat(logs.paused?[]:[streamWatch()]));
 };
 CoreModules['monitor.logs']=function(a){lang=a.language||lang;var e=a.data;
  if(e.kind==='connected')logs.connection=tr('实时连接已建立','Live connection established');
  if(e.kind==='error')logs.connection=errorMessage(e);
  if(e.kind==='frames'){
   var items=[];(e.frames||[]).forEach(function(frame){try{var record=JSON.parse(frame.data);if(record.type&&record.type!=='log')return;items.push(record);}catch(ignored){items.push({data:frame.data});}if(frame.id)logs.lastId=frame.id;});addLogs(items);
  }return logView();
 };
 CoreModules['monitor.logAction']=function(a){lang=a.language||lang;var action=a.data.action;
  if(action==='pause'){logs.paused=!logs.paused;logs.connection=logs.paused?tr('已暂停接收','Paused'):tr('正在重新连接…','Reconnecting…');return batch([logs.paused?{kind:'cancelWatch',id:'logs'}:streamWatch(),render('logControls',logControls()),logView()]);}
  if(action==='clear'){logs.rows=[];logs.seen={};logs.bytes=0;}
  if(action==='filter'){logs.query=String(a.data.fields.logQuery||'').slice(0,256);logs.level=String(a.data.fields.logLevel||'');}
  return logView();
 };
 function localized(v){if(v&&typeof v==='object'&&!Array.isArray(v))return String(v[lang]||v[lang==='zh'?'zh-CN':'en-US']||v.en||v['en-US']||v.zh||v['zh-CN']||'');return Array.isArray(v)?v.join(', '):String(v==null?'':v);}
 function repository(value){var url=String(value||'').trim();return /^https?:\/\/[^\s]+$/i.test(url)&&!/[\r\n]/.test(url)?url:'';}
 function normalizeMarket(data){if(data&&data.plugins)data=data.plugins;var entries=[];
  function add(item,id){if(!item||typeof item!=='object')return;var repo=repository(item.repo||item.repository||item.url);entries.push({marketId:String(item.market_plugin_id||(typeof item.author==='string'&&item.name?item.author+'/'+item.name:'')),id:String(item.id||id||item.name||repo),name:localized(item.display_name||item.name||id),author:localized(item.author),desc:localized(item.short_desc||item.desc||item.description),repo:repo,version:localized(item.version),stars:Number(item.stars)||0,tags:localized(item.tags||item.category),requirement:localized(item.astrbot_version||item.astrbot_version_requirement),supported:item.astrbot_version_supported!==false,rawName:String(item.name||'')});}
  if(Array.isArray(data))data.forEach(function(item){add(item,'');});else if(data&&typeof data==='object')Object.keys(data).forEach(function(k){if(k!=='$meta')add(data[k],k);});
  return entries;
 }
 CoreModules['monitor.marketNormalize']=function(a){lang=a.language||lang;return normalizeMarket(a.data);};
 function isInstalled(item){return market.installed.some(function(p){return p.repo&&String(p.repo).replace(/\.git$/,'').toLowerCase()===item.repo.replace(/\.git$/,'').toLowerCase()||p.name&&p.name===item.rawName;});}
 function literalTree(node){if(node.type==='text')node.literal=true;(node.children||[]).forEach(literalTree);return node;}
 function marketView(){var q=market.query.toLowerCase(),items=market.items.filter(function(p){var installed=isInstalled(p);return (!q||(p.name+' '+p.author+' '+p.desc+' '+p.tags+' '+p.id).toLowerCase().indexOf(q)>=0)&&(market.filter==='all'||market.filter==='installed'&&installed||market.filter==='available'&&!installed);});
  items.sort(function(a,b){return market.sort==='name'?a.name.localeCompare(b.name):b.stars-a.stars;});var size=30,pages=Math.max(1,Math.ceil(items.length/size));market.page=Math.max(0,Math.min(market.page,pages-1));
  var nodes=[text(market.notice||'',12,'muted'),text(items.length+tr(' 个结果 · 第 ',' results · page ')+(market.page+1)+' / '+pages,12,'muted')];
  items.slice(market.page*size,(market.page+1)*size).forEach(function(p){nodes.push(literalTree(UI.nav({title:p.name+(isInstalled(p)?tr(' · 已安装',' · Installed'):''),detail:(p.desc||p.author).slice(0,180)+'\n★ '+p.stars+(p.version?' · '+p.version:''),icon:'◇',active:true,action:event('monitor.marketAction','detail',{id:p.id})})));});
  if(!items.length)nodes.push(text(tr('没有符合条件的插件','No matching plugins')));
  nodes.push(button(tr('上一页','Previous'),'monitor.marketAction','previous'));nodes.push(button(tr('下一页','Next'),'monitor.marketAction','next'));return render('marketRows',UI.column(nodes));
 }
 function marketOpen(){market={items:[],installed:[],query:'',filter:'all',sort:'stars',page:0,notice:tr('正在读取插件市场…','Loading marketplace…')};
  return batch([screen(tr('插件市场','Plugin marketplace'),tr('插件安装在当前 AstrBot 服务器','Plugins install on the selected AstrBot server'),UI.column([
   text(tr('搜索名称、作者、描述或标签','Search name, author, description or tags'),13,'muted'),{type:'input',id:'marketQuery',value:'',onChange:event('monitor.marketAction','filter')},
   {type:'choice',id:'marketFilter',selected:0,options:[{label:tr('全部','All'),value:'all'},{label:tr('已安装','Installed'),value:'installed'},{label:tr('未安装','Not installed'),value:'available'}],action:event('monitor.marketAction','filter')},
   {type:'choice',id:'marketSort',selected:0,options:[{label:tr('按星标排序','Most stars'),value:'stars'},{label:tr('按名称排序','Name'),value:'name'}],action:event('monitor.marketAction','filter')},
   button(tr('刷新市场','Refresh marketplace'),'monitor.marketAction','refresh'),slot('marketRows')
  ])),marketView(),watch('market','monitor.marketData',{paths:paths('/api/v1/plugins/market','/api/plugin/market_list')}),watch('installed','monitor.marketInstalled',{paths:paths('/api/v1/plugins','/api/plugin/get')})]);
 }
 CoreModules['monitor.marketData']=function(a){lang=a.language||lang;var e=a.data;if(e.kind==='data'){market.items=normalizeMarket(e.data);market.notice=e.message||tr('来源：当前服务器配置的插件市场','Source: the marketplace configured on this server');}else market.notice=errorMessage(e);return marketView();};
 CoreModules['monitor.marketInstalled']=function(a){if(a.data.kind==='data'){var data=a.data.data;market.installed=Array.isArray(data)?data:data&&data.plugins||[];}return marketView();};
 function detail(p){market.current=p;
  var installed=isInstalled(p),nodes=[text(p.name,22),text(p.author,14,'muted'),text(p.desc||tr('暂无介绍','No description'),15),text(tr('版本：','Version: ')+(p.version||'—')+' · ★ '+p.stars),text(tr('标签：','Tags: ')+p.tags),text(tr('服务器版本要求：','Server version requirement: ')+(p.requirement||tr('以服务器安装检查为准','Validated by the server during installation'))),text(p.repo,12,'muted')];
  if(!p.supported)nodes.push(text(tr('市场标记此插件不兼容当前服务器版本。','The marketplace marks this plugin incompatible with the current server.')));
  if(p.repo)nodes.push({type:'button',text:tr('仓库与使用说明','Repository and documentation'),action:{kind:'openUrl',url:p.repo}});
  if(installed)nodes.push(text(tr('已经安装，可在插件管理中配置或启停。','Already installed. Configure or enable it in plugin management.')));
  else if(p.repo&&p.supported){nodes.push(text(tr('下载代理（可选）','Download proxy (optional)'),12,'muted'));nodes.push({type:'input',id:'proxy',value:''});nodes.push(button(tr('安装到当前服务器','Install on this server'),'monitor.marketAction','install',{id:p.id}));}
  return screen(tr('插件详情','Plugin details'),tr('安装前请核对来源与版本要求','Review source and version requirements before installing'),UI.column(nodes),event('monitor.open','',{page:'market'}));
 }
 CoreModules['monitor.marketAction']=function(a){lang=a.language||lang;var d=a.data,action=d.action;
  if(action==='filter'){market.query=String(d.fields.marketQuery||'').slice(0,256);market.filter=d.fields.marketFilter||'all';market.sort=d.fields.marketSort||'stars';market.page=0;return marketView();}
  if(action==='previous'||action==='next'){market.page+=action==='next'?1:-1;return marketView();}
  if(action==='refresh')return marketOpen();
  var p=market.items.filter(function(x){return x.id===d.id;})[0];if(!p)return {kind:'toast',text:tr('插件已不在当前列表，请刷新。','Plugin is no longer in this list. Refresh it.')};
  if(action==='detail')return detail(p);
  if(action==='install'&&!isInstalled(p)&&p.supported&&p.repo){var body=session.modern?{repository:p.repo,ignore_version_check:false}:{url:p.repo,ignore_version_check:false};if(p.marketId){body.install_method='market';body.market_plugin_id=p.marketId;}if(d.fields.proxy)body.proxy=String(d.fields.proxy);
   return {kind:'request',path:session.modern?'/api/v1/plugins/install/github':'/api/plugin/install',method:'POST',body:body,handler:'monitor.installResult',plugin:p,
    confirmTitle:tr('安装插件：','Install plugin: ')+p.name,confirmMessage:tr('目标实例：','Target: ')+session.name+'\n'+p.repo+'\n\n'+tr('安装会在该服务器下载并运行第三方插件代码。确认信任此来源后继续；服务器版本检查会保留。','This downloads and runs third-party plugin code on that server. Continue only if you trust the source. Server version checks remain enabled.')};
  }return {kind:'none'};
 };
 CoreModules['monitor.installResult']=function(a){lang=a.language||lang;var envelope=a.data;
  if(envelope.status==='warning')return {kind:'toast',text:envelope.message||tr('服务器返回安装警告，请查看实时日志。','Server reported an installation warning. Check live logs.')};
  return batch([{kind:'toast',text:envelope.message||tr('安装完成，请到插件管理配置。','Installed. Configure it in plugin management.')},marketOpen()]);
 };
 CoreModules['monitor.open']=function(a){lang=a.language||'zh';session=clone(a.session||{});var page=a.data.page;return page==='status'?statusOpen(a):page==='logs'?logsOpen():marketOpen();};
}());
