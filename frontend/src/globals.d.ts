// 环境声明（无顶层 import，保持 ambient）：通配模块与环境声明放这里
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module 'element-plus/dist/locale/zh-cn.mjs'
