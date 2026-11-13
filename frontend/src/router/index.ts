import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/pages/LoginPage.vue') },
    {
      path: '/',
      component: () => import('@/layouts/ConsoleLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/pages/DashboardPage.vue'), meta: { title: '看板' } },
        { path: 'tools', name: 'tools', component: () => import('@/pages/ToolListPage.vue'), meta: { title: '工具目录' } },
        { path: 'tools/:toolId', name: 'tool-detail', component: () => import('@/pages/ToolDetailPage.vue'), meta: { title: '工具详情' } },
        { path: 'imports', name: 'imports', component: () => import('@/pages/ImportWizardPage.vue'), meta: { title: 'OpenAPI 导入' } },
        { path: 'playground', name: 'playground', component: () => import('@/pages/PlaygroundPage.vue'), meta: { title: '调用测试' } },
        { path: 'subscriptions', name: 'subscriptions', component: () => import('@/pages/SubscriptionsPage.vue'), meta: { title: '订阅治理' } },
        { path: 'approvals', name: 'approvals', component: () => import('@/pages/ApprovalsPage.vue'), meta: { title: 'HITL 审批' } },
        { path: 'audit', name: 'audit', component: () => import('@/pages/AuditPage.vue'), meta: { title: '审计' } },
        { path: 'systems', name: 'systems', component: () => import('@/pages/SystemsPage.vue'), meta: { title: '系统与凭证' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path !== '/login' && !auth.adminToken) return '/login'
})

export default router
