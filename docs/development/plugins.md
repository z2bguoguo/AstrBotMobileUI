# 客户端插件开发

客户端插件与 AstrBot 服务端 Python 插件不同：它是一个可导入的 JSON 清单，为原生工作台添加操作按钮。版本 1 不支持下载执行 DEX、JS 或任意 Android 代码。HTML 插件仍通过服务器令牌页面加载。

## 最小清单

```json
{
  "apiVersion": 1,
  "name": "Runtime Inspector",
  "description": "读取当前服务器版本",
  "operations": [
    {
      "title": "查看版本",
      "method": "GET",
      "path": "/api/v1/stats/version",
      "pathParams": {},
      "query": {},
      "body": {}
    }
  ]
}
```

保存为 `.json`，在“客户端插件 → 导入插件 JSON”选择文件，确认后安装。仓库 `examples/runtime-info.json` 可作为起点。

## 字段与权限

| 字段 | 说明 |
| --- | --- |
| `apiVersion` | 必须为数字 1 |
| `name` / `description` | 展示名称与用途；不得含凭据 |
| `operations` | 最多 50 个，清单大小最多 256 KiB |
| `title` / `description` | 操作名称与说明 |
| `method` | GET / POST / PUT / PATCH / DELETE |
| `path` | 本实例 `/api/` 路径，禁止跨站、查询字符串和路径穿越 |
| `pathParams` / `query` / `body` | 原生请求页初始 JSON 对象 |

`/api/v1/personas/{persona_id}` 这类路径使用 `pathParams` 提供占位符，参数值会编码。不要把用户值直接拼接到路径。客户端使用当前实例权限执行，写操作仍需用户确认。插件没有独立服务器权限，应在说明中记录所需 scopes。

## 兼容与测试

公开插件应记录目标 AstrBot 版本，分别测试读取成功、401、403、404、服务端 `status:error` 和目标版本不支持的行为。不要盲目在旧版失败后重试写操作。当前插件清单没有自动路由转换机制，可为不同版本提供明确区分的操作。

扩展 APK / DEX ABI、后台脚本和第三方插件商店不属于版本 1 协议。
