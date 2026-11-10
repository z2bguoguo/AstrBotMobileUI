/* Reviewed help, loaded as signed script/data alongside the native UI module. */
(function () {
  'use strict';
  function data(){return typeof CoreData!=='undefined'&&CoreData['help.json']||{entries:{}};}
  function lookup(path){
    var keys=(path||[]).filter(function(k){return !/^[0-9]+$/.test(k);}),entries=data().entries;
    // Prefer the complete context, then a reviewed leaf alias. Never search by
    // substring: a plugin's custom_api_key must not acquire api_key semantics.
    return entries[keys.join('.')]||entries[keys[keys.length-1]]||null;
  }
  function resolve(value,language){var fields=typeof CoreData!=='undefined'&&CoreData['fields.json'];return fields&&fields[language]&&fields[language][value]||value;}
  CoreModules['help.lookup']=function(a){return lookup(a.path);};
  CoreModules['form.metadata']=function(a){
    var raw=Core.invoke('form.metadata.raw',a),meta=JSON.parse(JSON.stringify(raw)),entry=lookup(a.path);
    if(!entry){if(!meta.hint)meta.hint='mobile_help.unknown.hint';return meta;}
    // Preserve newer/custom server documentation instead of replacing it with a
    // snapshot. Schema type/default/options/constraints are always authoritative.
    var serverHint=meta.hint;
    // A reused field name is not evidence that a plugin shares built-in behavior.
    // Respect a custom description even when its author omitted a hint.
    if(!serverHint&&meta.description){
      var descriptionKnown=meta.description===entry.sourceDescription;
      ['zh','en'].forEach(function(language){
        if(resolve(meta.description,language)===entry.sourceDescription)descriptionKnown=true;
      });
      if(!descriptionKnown){meta.hint='mobile_help.unknown.hint';return meta;}
    }
    if(serverHint){
      var known=serverHint===entry.hint||serverHint===entry.sourceHint;
      ['zh','en'].forEach(function(language){if(resolve(serverHint,language)===entry.sourceHint)known=true;});
      if(!known)return meta;
    }
    meta.mobile_server_hint=serverHint||'';
    meta.hint=entry.hint;meta.mobile_summary=entry.summary;
    return meta;
  };
}());
