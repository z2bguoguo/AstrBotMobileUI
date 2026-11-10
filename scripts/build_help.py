"""Build deterministic bilingual help from reviewed, pinned source evidence.

No server code or user configuration is executed/read. Editorial overrides take
precedence over upstream hints, notably for obsolete and ambiguous fields.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/core'

def generate():
    source = json.loads((ROOT / 'scripts/dev/help-source.json').read_text())
    authored = json.loads((ROOT / 'scripts/dev/help-authored.json').read_text())
    names = json.loads((ROOT / 'scripts/dev/field-names.json').read_text())
    metadata = source['metadata']
    fields = json.loads((ASSETS / 'fields.json').read_text())
    # Remove obsolete generated entries when the inventory changes.
    for language in ('zh', 'en'):
        fields[language] = {k:v for k,v in fields[language].items() if not k.startswith('mobile_help.')}
    catalogue = {'revision': source['revision'], 'entries': {}}
    for key in sorted(set(names) | set(metadata)):
        candidates = [p for p in metadata if p.endswith('.' + key)]
        canonical = key if key in metadata else sorted(candidates, key=lambda p:(len(p),p))[0] if candidates else key
        meta = metadata.get(canonical, {})
        editorial = authored.get(key, authored.get(canonical))
        # Flat metadata leaf aliases are used only as fallback. Contextual full
        # paths always win at runtime; generic id/type/enable have authored help.
        hint = meta.get('hint', '')
        if not editorial and not hint:
            raise ValueError(f'Unreviewed field: {key}')
        refs = []
        if meta:
            refs.append(f"astrbot/core/config/default.py#L{meta['line']}")
        doc = source['docs'].get(canonical, source['docs'].get(key))
        if doc:
            refs.append(f"docs/zh/dev/astrbot-config.md#L{doc['line']}")
        for consumer in source['consumers'].get(canonical.rsplit('.',1)[-1], [])[:2]:
            refs.append(f"{consumer['path']}#L{consumer['line']}")
        if key in ('command','args','env','headers','transport','url'):
            refs = ['astrbot/core/agent/mcp_client.py#L475']
        if key in ('whitelist','id_whitelist','platform_settings.enable_id_white_list','enable_id_white_list','wl_ignore_admin_on_group','wl_ignore_admin_on_friend'):
            refs = ['astrbot/core/pipeline/whitelist_check/stage.py#L17', 'docs/zh/use/command.md']
        if not refs:
            refs = ['docs/zh/dev/astrbot-config.md', 'astrbot/core/config/default.py']
        default = source['defaults'].get(canonical, source['defaults'].get(key))
        example = editorial['example'] if editorial else default
        if example is None or example == '' or example == [] or example == {}:
            options = meta.get('options', [])
            if options:
                example = [options[0]] if meta.get('type') in ('list','array') else options[0]
            elif re.search(r'(?:api_?key|access_token|secret|password)$', key, re.I) and meta.get('type') not in ('int','float','number','bool','list','array'):
                example = 'YOUR_SERVICE_CREDENTIAL'
            elif re.search(r'url|endpoint|base', key, re.I):
                example = 'https://service.example.com'
            elif re.search(r'path|file', key, re.I) and meta.get('type') not in ('list','array'):
                example = '/srv/astrbot/data/example'
            elif meta.get('type') in ('list','array'):
                example = ['example-item']
            elif meta.get('type') in ('dict','object','template_list'):
                # Show a structure only; never imply a fictional child is supported.
                example = {}
            elif meta.get('type') in ('int','integer','float','number'):
                example = 10
            elif meta.get('type') in ('bool','boolean'):
                example = True
            else:
                example = 'example-value'
        # Examples must never contain a filled credential or a live source default
        # object with a credential embedded inside it.
        def safe(value, field=key):
            if isinstance(value,(bool,int,float)) or value is None:return value
            if re.search(r'(?:^|_)(?:password|passwd|secret|api_?key|access_token|auth_token|bot_token|token|authorization)(?:$|_)',field,re.I):
                return ['YOUR_API_KEY'] if isinstance(value,list) else 'YOUR_CREDENTIAL'
            if isinstance(value,dict):return {k:safe(v,k) for k,v in value.items()}
            if isinstance(value,list):return [safe(v,'item') for v in value]
            return value
        # Normalize illustrative values to the stored JSON shape. Metadata for
        # special UI selectors can disagree with real defaults (plugin_set).
        example_type = 'list' if key == 'plugin_set' else meta.get('type')
        if example_type in ('list','array','template_list') and not isinstance(example,list):
            example = [example]
        if example_type in ('int','integer','float','number') and not isinstance(example,(int,float)):
            raise ValueError(f'Non-numeric example for {key}')
        if example_type in ('bool','boolean') and not isinstance(example,bool):
            raise ValueError(f'Non-boolean example for {key}')
        if example_type in ('dict','object') and not isinstance(example,dict):
            raise ValueError(f'Non-object example for {key}')
        options = meta.get('options', []) if key != 'type' else []
        if options and not isinstance(example,(list,dict)) and example not in options:
            matching = [v for v in options if str(v).lower() == str(example).lower()]
            if matching:example = matching[0]
            else:raise ValueError(f'Example outside options for {key}')
        example = safe(example)
        prefix = 'mobile_help.' + key
        catalogue['entries'][key] = {'hint':prefix+'.hint','summary':prefix+'.summary', 'sources':refs, 'example':example, 'type':example_type, 'options':meta.get('options',[]) if key!='type' else [], 'sourceHint':hint, 'sourceDescription':meta.get('description','')}
        for language in ('zh','en'):
            title = names.get(key,{}).get(language) or meta.get('description' if language=='zh' else 'description_en') or key
            purpose = editorial[language] if editorial else meta.get('hint' if language=='zh' else 'hint_en', hint)
            # English copies must be reviewed, never silently display Chinese.
            if language == 'en' and re.search('[\u4e00-\u9fff]',purpose):
                raise ValueError(f'English explanation missing: {key}')
            example_text = json.dumps(example,ensure_ascii=False,indent=2)
            if len(example_text) > 1800:
                example_text = json.dumps({key: '…'},ensure_ascii=False)
            links = '\n'.join('https://github.com/AstrBotDevs/AstrBot/blob/'+source['revision']+'/'+ref for ref in refs)
            if language == 'zh':
                effect = '修改此项会改变上述功能的行为；仅在对应服务、执行器或平台启用并支持该参数时生效。先在草稿中修改，核对后再保存。'
                example_note = '这是格式示例，不是建议直接覆盖现有值；标识、地址和资源需替换为你服务器实际存在的内容。空对象表示应展开子项编辑。'
                body = f'用途与行为\n{purpose}\n\n生效与影响\n{effect}\n\n填写示例\n{example_text}\n{example_note}\n\n核对依据（上游固定版本）\n{links}'
            else:
                effect = 'This changes the behavior described above and applies only when the selected service, runner or platform supports and enables this option. Review the draft before saving.'
                example_note = 'Format example, not a replacement recommendation. Substitute existing server IDs, addresses and resources. An empty object means edit its children.'
                body = f'Purpose and behavior\n{purpose}\n\nEffect and applicability\n{effect}\n\nExample\n{example_text}\n{example_note}\n\nEvidence (pinned upstream revision)\n{links}'
            fields[language][prefix+'.hint'] = body
            fields[language][prefix+'.summary'] = purpose.split('\n')[0][:180]
    unknown = {
        'zh':'此字段由服务器或第三方插件提供，目前没有可核实的内置业务说明。请优先阅读该插件的配置文档与服务器返回的说明；不能仅凭字段名称判断用途。\n\n填写示例：开关使用 true/false，列表逐项添加，对象展开编辑，文本保留原有格式。具体允许值和实际影响由插件决定。修改先保存在草稿，保存前请核对。',
        'en':'This field comes from the server or a third-party plugin and has no verified built-in semantic description. Read the plugin documentation and server metadata; a field name alone does not establish its purpose.\n\nFormat examples: true/false for switches, separate entries for lists, expanded children for objects, original formatting for text. Allowed values and effects are plugin-defined. Review the draft before saving.'}
    for language in ('zh','en'):
        fields[language]['mobile_help.unknown.hint'] = unknown[language]
    return fields, catalogue

def build(check=False):
    fields,catalogue=generate()
    outputs={'fields.json':fields,'help.json':catalogue}
    for filename,value in outputs.items():
        data=json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n'
        path=ASSETS/filename
        if check:
            if not path.exists() or path.read_text()!=data:
                raise ValueError(f'{filename} is stale; run python3 scripts/build_help.py')
        else:path.write_text(data)
    print(f"Help coverage: {len(catalogue['entries'])}/{len(catalogue['entries'])} inventoried keys; zh + en")
    return catalogue

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    build(parser.parse_args().check)
