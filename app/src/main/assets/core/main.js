var CoreModules = {};
/* AstrBot Mobile script core. Pure JSON business logic; no Java or network access. */
var Core = (function () {
  'use strict';
  function clone(x) { return JSON.parse(JSON.stringify(x)); }
  function object(x) { return x !== null && typeof x === 'object' && !Array.isArray(x) && !Object.prototype.hasOwnProperty.call(x,'__astrbot_exact_number__'); }
  function enc(x) { if(x && x.__astrbot_exact_number__)x=x.__astrbot_exact_number__; return encodeURIComponent(String(x)).replace(/[!'()*]/g, function(c){return '%'+c.charCodeAt(0).toString(16).toUpperCase();}); }
  function ref(s, spec) { if (!s || !s.$ref || s.$ref.indexOf('#/') !== 0) return s || {}; var node=spec; s.$ref.slice(2).split('/').forEach(function(k){node=node && node[k.replace(/~1/g,'/').replace(/~0/g,'~')];}); return object(node)?node:s; }
  function sample(s,spec,d) {
    if(d>7)return null; s=ref(s,spec);
    if('default' in s)return s.default; if('example' in s)return s.example;
    if(s.enum && s.enum.length)return s.enum[0];
    var union=s.anyOf || s.oneOf;
    if(union)for(var i=0;i<union.length;i++)if(union[i].type!=='null')return sample(union[i],spec,d+1);
    var type=s.type || (s.properties?'object':'string');
    if(type==='object'){var out={};Object.keys(s.properties||{}).forEach(function(k){out[k]=sample(s.properties[k],spec,d+1);});return out;}
    return type==='array'?[]:type==='boolean'?false:type==='integer'||type==='number'?0:'';
  }
  var media=['application/json','multipart/form-data','application/x-www-form-urlencoded','application/octet-stream'];
  function content(a){return ref(a.definition.requestBody||{},a.spec).content||{};}
  function redact(v){if(Array.isArray(v))return v.map(redact);if(!object(v))return v;var out={};Object.keys(v).forEach(function(k){out[k]=/password|passwd|token|secret|api.?key|authorization|cookie/i.test(k)?'':redact(v[k]);});return out;}
  function suggestions(v,out,d){if(d>8 || out.length>300)return;if(Array.isArray(v)){v.forEach(function(x){suggestions(x,out,d+1);});return;}if(!object(v))return;Object.keys(v).forEach(function(k){if(/password|passwd|token|secret|api.?key|authorization|cookie/i.test(k))return;var x=v[k];if((k==='id'||k==='name'||/group_id|umo|model/.test(k))&&typeof x==='string'&&x.trim()&&out.indexOf(x)<0)out.push(x);if(typeof x==='object')suggestions(x,out,d+1);});}
  function find(o,key,d){if(!object(o)||d>12)return null;if(object(o[key]))return o[key];var keys=Object.keys(o);for(var i=0;i<keys.length;i++){var k=keys[i];if(['options','default','config_template','condition','i18n'].indexOf(k)>=0)continue;var f=find(o[k],key,d+1);if(f)return f;}return null;}
  function metadata(root,path){if(!root||!path.length)return {};var found=find(root,path.join('.'),0);if(found)return found;var scope=root;for(var i=0;i<path.length;i++){var field=find(scope,path[i],0);if(!field)return {};if(i===path.length-1)return field;scope=field.items;}return {};}
  function defaults(m){if('default' in m)return clone(m.default);var type=m.type||'string';if(['bool','boolean'].indexOf(type)>=0)return false;if(['int','integer','float','number'].indexOf(type)>=0)return 0;if(['list','array','template_list'].indexOf(type)>=0)return [];if(type==='object'){var o={};Object.keys(m.items||{}).forEach(function(k){o[k]=defaults(m.items[k]);});return o;}return '';}
  function diff(a,b,path,out){if(object(a)&&object(b)){var keys=Object.keys(a).concat(Object.keys(b)).filter(function(k,i,s){return s.indexOf(k)===i;}).sort();keys.forEach(function(k){diff(a[k],b[k],path?path+'.'+k:k,out);});}else if(JSON.stringify(a)!==JSON.stringify(b))out.push(path);}
  var resources={PERSONAS:['personas','/api/persona/list','persona_id'],MCP:['mcp/servers','/api/tools/mcp/servers','name'],PLUGINS:['plugins','/api/plugin/get','name'],PROVIDERS:['providers','/api/config/provider/list','id'],BOTS:['bots','/api/config/platform/list','id']};
  function plan(a){
    var m=a.modern, op=a.op, t=a.resource, id=a.id||'', e=enc(id), c=a.config||{}, r=resources[t], p,b,verb='GET',legacy;
    if(op==='overview') {p='/api/v1/stats';legacy='/api/stat/get';}
    if(op==='configuration'||op==='saveConfiguration'){p=id==='system'?'/api/v1/system-config':'/api/v1/config-profiles/'+e;legacy=id==='system'?'/api/config/get':'/api/config/abconf?id='+e;if(op==='saveConfiguration'){legacy='/api/config/astrbot/update';verb=m?'PUT':'POST';b=m?c:{conf_id:id==='system'?'default':id,config:c};}}
    if(op==='profiles'){p='/api/v1/config-profiles';legacy='/api/config/abconfs';}
    if(op==='list'){p='/api/v1/'+r[0];legacy=r[1];}
    if(op==='detail'){
      if(t==='PLUGINS'){p='/api/v1/plugins/'+e+'/config';legacy='/api/config/get?plugin_name='+e;}
      if(t==='PERSONAS'){p='/api/v1/personas/by-id?persona_id='+e;legacy='/api/persona/detail';if(!m){verb='POST';b={persona_id:id};}}
      if(t==='PROVIDERS'){p='/api/v1/providers/by-id?provider_id='+e;legacy=null;}
      if(t==='BOTS'){p='/api/v1/bots/by-id?bot_id='+e;legacy=null;}
    }
    if(op==='save'){
      verb=m?'PUT':'POST';
      if(t==='PERSONAS'){p=a.create?'/api/v1/personas':'/api/v1/personas/by-id';legacy=a.create?'/api/persona/create':'/api/persona/update';if(a.create)verb='POST';b=clone(c);b.persona_id=id;}
      if(t==='PLUGINS'){p='/api/v1/plugins/'+e+'/config';legacy='/api/config/plugin/update?plugin_name='+e;b=m?{config:c}:c;}
      if(t==='MCP'){p=a.create?'/api/v1/mcp/servers':'/api/v1/mcp/servers/by-name';legacy=a.create?'/api/tools/mcp/add':'/api/tools/mcp/update';if(a.create){verb='POST';b=clone(c);b.name=id;}else{b={config:c};b[m?'server_name':'name']=id;}}
      if(t==='PROVIDERS'||t==='BOTS'){p='/api/v1/'+(t==='BOTS'?'bots':'providers')+'/by-id';legacy='/api/config/'+(t==='BOTS'?'platform':'provider')+'/update';b={config:c};b[m?(t==='BOTS'?'bot_id':'provider_id'):'id']=id;}
    }
    if(op==='pluginEnabled'){p='/api/v1/plugins/'+e+'/enabled';legacy='/api/plugin/'+(a.enabled?'on':'off');verb=m?'PATCH':'POST';b=m?{enabled:a.enabled}:{name:id};}
    if(!p && !legacy)throw new Error('Unsupported operation');
    return {path:m?p:legacy,legacy:legacy,method:verb,body:b===undefined?null:b};
  }
  function statusSections(data) {
    var sections=[], overview={title:'运行概况',metrics:[]}, resource={title:'资源占用',metrics:[]}, other={title:'其他状态',metrics:[]};
    function number(v){return typeof v==='number' && isFinite(v);}
    function metric(target,path,kind,value,label,hint){target.metrics.push({path:path,kind:kind,value:value===undefined?null:value,label:label||'',hint:hint||''});}
    function duration(v){
      if(!object(v))return number(v)&&v>=0?v:null;
      var total=0,found=false,valid=true;
      [['days',86400],['hours',3600],['minutes',60],['seconds',1]].forEach(function(pair){if(pair[0] in v){found=true;if(!number(v[pair[0]])||v[pair[0]]<0)valid=false;else total+=v[pair[0]]*pair[1];}});
      return found&&valid?total:null;
    }
    function visit(o,path,target,depth){
      if(depth>6)return;
      Object.keys(o).forEach(function(k){
        if(/password|passwd|token|secret|api.?key|authorization|cookie/i.test(k))return;
        var v=o[k], at=path.concat([k]);
        if((k==='running'||k==='uptime'||k==='uptime_seconds'||k==='running_time')&&duration(v)!==null){metric(overview,at,'duration',duration(v),'运行时长');return;}
        if(k==='memory'&&object(v)){
          Object.keys(v).forEach(function(m){if((m==='process'||m==='system')&&number(v[m])&&v[m]>=0)metric(resource,at.concat([m]),'bytes',v[m]*1048576);else {var rest={};rest[m]=v[m];visit(rest,at,resource,depth+1);}});
          if(number(v.process)&&number(v.system)&&v.system>0&&v.process>=0)metric(resource,at.concat(['process_share']),'percent',v.process/v.system*100,'进程内存占比','进程内存 / 系统总内存');
          return;
        }
        if(object(v)){visit(v,at,target,depth+1);return;}
        if(Array.isArray(v)){
          // Keep large history arrays out of the main screen; full data stays in Raw status.
          if(k==='plugins'||k==='platform'||k==='message_time_series'){metric(other,at,'count',v.length);return;}
          metric(target,at,'text',v.length);return;
        }
        if(/^(start_time|boot_time|started_at|timestamp)$/.test(k)){
          metric(overview,at,'timestamp',v,'','按手机时区显示');return;
        }
        if(number(v)&&/(_percent|_percentage)$|^(percent|percentage)$/.test(k)){
          metric(resource,at,'percent',v);return;
        }
        if(number(v)&&/_ratio$/.test(k)&&v>=0&&v<=1){metric(resource,at,'percent',v*100);return;}
        if(number(v)&&/_bytes$/.test(k)){metric(resource,at,'bytes',v);return;}
        if(number(v)&&/_(mib|mb)$/.test(k)){metric(resource,at,'bytes',v*1048576);return;}
        if(number(v)&&/_(gib|gb)$/.test(k)){metric(resource,at,'bytes',v*1073741824);return;}
        if(k==='thread_count'){metric(resource,at,'count',v);return;}
        if(k==='notice'||/version|count|uptime|running/.test(k)){metric(overview,at,number(v)?'count':'text',v);return;}
        metric(target,at,number(v)?'count':'text',v);
      });
    }
    if(object(data))visit(data,[],other,0);else metric(other,['status'],'text',data);
    [overview,resource,other].forEach(function(s){if(s.metrics.length)sections.push(s);});
    return sections;
  }

  function statusFormat(a){
    var value=a.value,zh=a.chinese,kind=a.kind;
    if(value===null||value===undefined)return '—';
    if(kind==='timestamp'){
      if(!a.parts)return String(value);
      var parts=a.parts,pattern=a.pattern||'yyyy-MM-dd HH:mm:ss';
      function pad(n){return n<10?'0'+n:String(n);}
      return pattern.replace(/yyyy|MM|dd|HH|mm|ss/g,function(token){return token==='yyyy'?String(parts.year):pad(parts[{MM:'month',dd:'day',HH:'hour',mm:'minute',ss:'second'}[token]]);});
    }
    if(typeof value==='boolean')return zh?(value?'是':'否'):(value?'Yes':'No');
    var exact=value&&value.__astrbot_exact_number__,raw=exact||String(value);
    if(typeof value!=='number'&&!exact)return raw;
    var n=Number(raw);if(!isFinite(n))return '—';
    function grouped(s){var pieces=s.split('.');return pieces[0].replace(/\B(?=(\d{3})+(?!\d))/g,',')+(pieces.length>1?'.'+pieces[1]:'');}
    function decimal(n,digits){return n.toFixed(digits).replace(/\.?0+$/,'');}
    if(kind==='percent')return n.toFixed(1)+'%';
    if(kind==='duration'){
      if(n<0)return '—';var seconds=Math.floor(n),days=Math.floor(seconds/86400),hours=Math.floor(seconds%86400/3600),minutes=Math.floor(seconds%3600/60),remaining=seconds%60;
      return (days?days+(zh?' 天 ':'d '):'')+(hours||days?hours+(zh?' 小时 ':'h '):'')+(minutes||hours||days?minutes+(zh?' 分 ':'m '):'')+remaining+(zh?' 秒':'s');
    }
    if(kind==='bytes'){
      if(n<0)return '—';var units=['B','KiB','MiB','GiB','TiB','PiB'],unit=0;
      while(n>=1024&&unit<units.length-1){n/=1024;unit++;}
      return grouped(unit?decimal(n,2):n.toFixed(0))+' '+units[unit];
    }
    return grouped(exact?raw:decimal(n,3));
  }

  function invoke(name,a){
    if(name==='status.format')return statusFormat(a);
    if(CoreModules[name])return CoreModules[name](a);
    if(name==='status.sections')return statusSections(a.data);
    if(name==='ping')return {abi:1};
    if(name==='management.plan')return plan(a);
    if(name==='management.id')return String(a.item[resources[a.resource][2]]||a.item.plugin_name||a.item.plugin_id||'');
    if(name==='management.array'){if(Array.isArray(a.data))return a.data;var list=a.data[a.key]||(a.key==='bots'?a.data.platforms:null);if(Array.isArray(list))return list;throw new Error('服务器返回的列表格式暂不支持，请使用高级工具并反馈版本');}
    if(name==='management.detail'){var c=clone(a.item);if(a.resource==='MCP')['name','connected','tools','errlogs'].forEach(function(k){delete c[k];});return {config:c,metadata:{}};}
    if(name==='schema.resolve')return ref(a.schema,a.spec);
    if(name==='schema.sample')return sample(a.schema,a.spec,a.depth);
    if(name==='schema.request'){var ct=content(a);for(var i=0;i<3;i++)if(ct[media[i]])return ref(ct[media[i]].schema||{},a.spec);return {};}
    if(name==='schema.media'){var ct=content(a);for(var i=0;i<media.length;i++)if(ct[media[i]])return media[i];return media[0];}
    if(name==='helpers.redact')return redact(a.value);
    if(name==='helpers.suggestions'){var out=a.current||[];suggestions(a.value,out,a.depth);return out;}
    if(name==='helpers.warnings'){var out=[],k=a.name.toLowerCase();if(/group|umo|session/.test(k)&&/^[0-9]+$/.test(a.value))out.push('部分接口需要平台前缀、group: 或完整 UMO；请以本服务器字段说明为准');if(/url/.test(k)&&a.value.trim()&&!/^https?:\/\/.+/.test(a.value))out.push('此字段可能需要完整 http(s) 地址');return out;}
    if(name==='form.metadata'||name==='form.metadata.raw')return metadata(a.root,a.path);
    if(name==='form.default')return defaults(a.meta);
    if(name==='form.diff'){var out=[];diff(a.before,a.after,'',out);return out;}
    if(name==='endpoint.resolve'){
      var path=a.path.replace(/\{([^}:]+)(?::[^}]+)?\}/g,function(_,key){var raw=a.values[key];if(raw&&raw.__astrbot_exact_number__)raw=raw.__astrbot_exact_number__;var val=raw===undefined||raw===null?'':String(raw);if(!val.trim())throw new Error('缺少路径参数：'+key);if(val==='.'||val==='..')throw new Error('路径参数不能是 . 或 ..');return enc(val);});
      var q=[];Object.keys(a.query).forEach(function(k){var v=a.query[k];if(v===null)return;(Array.isArray(v)?v:[v]).forEach(function(x){q.push(enc(k)+'='+enc(x));});});return path+(q.length?'?'+q.join('&'):'');
    }
    if(name==='endpoint.spec'){var out=[];Object.keys(a.spec.paths).forEach(function(path){var methods=a.spec.paths[path];['get','post','put','patch','delete','head'].forEach(function(method){if(!methods[method])return;var d=clone(methods[method]);d.parameters=(methods.parameters||[]).concat(d.parameters||[]);out.push({path:path,method:method,definition:d});});});return out;}
    throw new Error('Unknown core function: '+name);
  }
  return {invoke:invoke};
}());
function dispatch(name,json){return JSON.stringify(Core.invoke(name,JSON.parse(json)));}
