// axios 请求配置扩展：自定义 token 注入标记（此文件含 import，属模块增强）
import type { AxiosRequestConfig } from 'axios'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 该请求用哪类 token：admin(默认) / agent / none(公开接口)。注意避开 axios 自带的 auth( Basic 认证 ) */
    authMode?: 'admin' | 'agent' | 'none'
    /** HITL 审批等场景：临时以指定 agentId 身份发送（优先于 authMode:'agent' 的默认 agentId） */
    agentIdOverride?: string
  }
}
