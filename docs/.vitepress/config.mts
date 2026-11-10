import { defineConfig } from 'vitepress'
export default defineConfig({
  title: 'AstrBot Mobile', description: 'A native Android workspace for AstrBot',
  base: '/AstrBotMobileUI/', cleanUrls: true, lastUpdated: true,
  locales: {
    root: { label: '简体中文', lang: 'zh-CN', title: 'AstrBot Mobile', themeConfig: {
      nav: [{ text: '指南', link: '/guide/start' }, { text: '插件开发', link: '/development/plugins' }],
      sidebar: [
        { text: '使用指南', items: [ {text:'开始使用',link:'/guide/start'}, {text:'连接与兼容性',link:'/guide/connections'}, {text:'功能与编辑器',link:'/guide/features'}, {text:'集群与预设',link:'/guide/cluster'}, {text:'自定义 HTML',link:'/guide/html'}, {text:'排错',link:'/guide/troubleshooting'} ]},
        { text: '开发者', items: [{text:'架构与构建',link:'/development/architecture'},{text:'脚本核心',link:'/development/script-core'},{text:'原生 UI 脚本',link:'/development/native-ui'},{text:'客户端插件',link:'/development/plugins'},{text:'签名与发布',link:'/development/releases'},{text:'测试与安全',link:'/development/testing'},{text:'接口基线',link:'/reference/upstream'}] }
      ]
    }},
    en: { label:'English',lang:'en-US',link:'/en/',themeConfig:{
      nav:[{text:'Guide',link:'/en/guide'},{text:'Plugin SDK',link:'/en/plugins'}],
      sidebar:[{text:'Documentation',items:[{text:'Getting started',link:'/en/guide'},{text:'Plugin development',link:'/en/plugins'},{text:'Build and release',link:'/en/development'}]}]
    }}
  },
  themeConfig: { logo: '/cat.svg', socialLinks: [{ icon:'github',link:'https://github.com/z2bguoguo/AstrBotMobileUI' }], search:{provider:'local'}, footer:{message:'An independent community client · AGPL-3.0',copyright:'AstrBot Mobile UI contributors'} }
})
