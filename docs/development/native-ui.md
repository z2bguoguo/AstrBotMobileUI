# 脚本驱动的原生界面

0.3 架构提供 UI ABI 1。界面继续使用 Android 原生 View，不把页面改成网页。脚本生成 JSON 控件树，原生壳负责将其映射为系统控件和执行明确的系统能力；布局、文案、整行点击策略、菜单、配置条目、状态展示、连接编辑和长文本操作由脚本决定。

## 文件与更新边界

- `core/main.js`：API、数据处理、状态分类和单位/时长格式化。
- `core/ui.js`：主要页面、通用控件模板、主题、交互描述和 UI 事件处理。
- `core/*.js`：可继续新增模块；加载器先执行 main.js，再按文件名顺序加载其他脚本。可在 `CoreModules` 注册新的处理函数。模块相互引用可放到函数体内，避免初始化顺序依赖。
- `core/*.json`：作为 `CoreData[文件名]` 供脚本读取，包含翻译和字段数据。
- `NativeViews`：通用原生控件与事件桥。
- `CoreStore` / `SignedCore`：验签、原子替换、启动确认和回退。

发布器自动收集核心目录中的 JS/JSON，所有文件连同架构和版本号一并签名。只修改这些脚本或数据文件不会改变原生架构指纹，但仍会打包一份包含最新脚本的完整 APK。新增底层控件类型、系统权限或原生桥接口仍需安装 APK；在已有 ABI 内增加页面、调整布局、修复点击区域、改变状态格式或增加主题只需更新核心。

## JSON 控件树

```javascript
CoreModules['ui.example'] = function (a) {
  return {
    type: 'column', padding: [18, 12, 18, 12],
    children: [
      {type: 'text', text: '示例页面', size: 22, bold: true},
      {type: 'progress', max: 1000, value: 420, height: 8},
      {type: 'button', text: '下一步', height: 48,
       action: {kind: 'event', handler: 'ui.exampleNext'}}
    ]
  };
};
CoreModules['ui.exampleNext'] = function (a) {
  return {kind: 'toast', text: '已处理'};
};
```

支持 `column`、`row`、`scroll`、`text`、`button`、`input`、`switch`、`checkbox`、`choice`、`slider`、`progress`、`space`。可以组合现有控件实现新页面。`id` 注册引用；稳定的宿主槽位（page、content、toolbar、subtitle 等）应保持约定类型，避免破坏兼容适配器。

常用属性包括 padding/margin 四边距、width/height（dp、fill、wrap）、weight、minHeight、size、color、background、radius、bold、gravity、enabled、visible、action。颜色支持调色板令牌和十六进制。`onChange`、`onSubmit` 和 `onLongPress` 可定义相应事件；点击事件携带当前有 id 的输入值。输入框的实际内容不做翻译。

单一操作的导航行设置 `atomic: true`：整行响应同一事件，内部标题、说明、图标和箭头作为装饰，不独立抢占点击。含开关或说明按钮的组合容器应保持独立控件，不对整个容器设置 atomic。

## 原生能力桥

脚本页面可使用事件处理器返回 `screen`、`request`、`event`、`toast`、`openUrl`、`openPlugin`、`preferences`、`saveState` 命令。`request` 只访问当前实例的 `/api/`，鉴权、Cookie 和 JWT 仍由原生客户端隔离；写入仍须确认，不跟随带凭据的跨域重定向。文件选择/保存、插件 WebView、加密存储和安装器继续使用系统桥及已有兼容适配器。

脚本没有 Java 包、任意文件系统或直接网络访问权限，也不执行下载的 DEX。第三方声明式客户端插件不能替换这些处理器；只有维护者签名的核心可以更新。数字精度由 JSON 边界保护；长文本编辑中的精确 JSON 解析仍由底层解析器完成。

## 验证

运行 Node UI 树/事件测试、JVM 模块加载/格式化/安全边界测试及 Android 仪器测试。新增模块应测试字段不被翻译、原始状态不被修改、独立按钮不误触主行、异步网络结果不更新已离开的页面。发布前确认完整 APK 与独立核心都包含新增模块。
