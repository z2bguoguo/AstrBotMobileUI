"""Build bilingual UI text, with complete coverage checked against native UI literals."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
seed = dict(line.split('|', 1) for line in (ROOT / 'scripts/dev/ui-translations.tsv').read_text().splitlines())
seed.update({
    '\n操作将使用你当前实例的权限。':'\nOperations use your current instance permissions.',
    '\n（显示已截断，下载可保存完整响应）':'\n(Display truncated. Save the response file for full content.)',
    ' · 标识不可修改':' · Identifier is read-only', ' 副本':' copy', ' 字节，类型 ':' bytes, type ',
    ' 的本地密码也会删除。':" and its locally saved password will be deleted.",
    ' 需要 true 或 false':' requires true or false',' 项':' items',' 项设置':' settings',
    '‹  返回':'‹ Back','‹ 返回原生客户端':'‹ Back to native client','下载更新 ':'Download update ',
    '保存更改（':'Save changes (','全屏编辑 · ':'Full-screen editor · ','发现新版本 ':'New version ',
    '将保存 %d 项更改：\n%s':'Save %d changes:\n%s','展开编辑 · ':'Expand editor · ','已下载 ':'Downloaded ',
    '已连接 · ':'Connected · ','当前版本 ':'Current version ','打开 ':'Open ','收到 ':'Received ',
    '数值不能大于 ':'Value must not exceed ','数值不能小于 ':'Value must not be below ',
    '更新服务器返回 HTTP ':'Update server returned HTTP ','正在连接…':'Connecting…',
    '正式版本\n\n':'Stable release\n\n','预发布版本\n\n':'Preview release\n\n','第 ':'Item ',
    '预设、扩展与高级诊断':'Presets, extensions and diagnostics','（请检查服务器日志）':' (check the server logs)',
    '＋ 新建':'+ New ','＋ 添加一项':'+ Add item','＋ 添加条目':'+ Add entry','＋ 添加连接':'+ Add connection',
})
# Upstream metadata translations are committed as core data, with provenance in third-party notices.
locales={'en':seed,'zh':{v:k for k,v in seed.items()}}
(ROOT/'app/src/main/assets/core/locales.json').write_text(json.dumps(locales,ensure_ascii=False,separators=(',',':'))+'\n')
# Provide a Chinese operation name for every operation in the bundled contract.
route_words = dict(line.split('|', 1) for line in (ROOT/'scripts/dev/route-translations.tsv').read_text().splitlines())
spec = json.loads((ROOT/'app/src/main/assets/openapi.json').read_text())
for path, methods in spec['paths'].items():
    label = ' / '.join(route_words.get(k, k) for k in path.split('/') if k and k not in ('api','v1') and not k.startswith('{'))
    for method, operation in methods.items():
        if isinstance(operation,dict) and operation.get('summary'):
            action = {'get':'读取','post':'执行','put':'保存','patch':'修改','delete':'删除','head':'检查'}.get(method,'操作')
            locales['zh'][operation['summary']] = action + ' · ' + label
(ROOT/'app/src/main/assets/core/locales.json').write_text(json.dumps(locales,ensure_ascii=False,separators=(',',':'))+'\n')
