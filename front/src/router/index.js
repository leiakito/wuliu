import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import MainLayout from '@/components/MainLayout.vue';
// 路由系统
const routes = [
    {
        // 不需要登陆, 访问 /login 链接到 @/views/LoginView.vue
        path: '/login',
        name: 'login',
        component: () => import('@/views/LoginView.vue')
    },
    {
        // 需要登陆才能访问
        path: '/',
        component: MainLayout,
        meta: { requiresAuth: true },
        children: [
            // children 子路由解析 默认定向到 dashboard
            { path: '', redirect: '/dashboard' },
            {
                path: 'dashboard',
                name: 'dashboard',
                component: () => import('@/views/DashboardView.vue'),
                meta: { requiresAuth: true, title: '单号总查询' }
            },
            {
                path: 'orders',
                name: 'orders',
                component: () => import('@/views/OrdersView.vue'),
                // ✅ 只要求登录，ADMIN 和 USER 都可以访问
                meta: { requiresAuth: true, title: '物流单号', keepAlive: true, roles: ['ADMIN', 'USER'] }
            },
            {
                path: 'user-submissions',
                name: 'user-submissions',
                component: () => import('@/views/UserSubmissionsView.vue'),
                meta: { requiresAuth: true, title: '单号提交' }
            },
            {
                path: 'hardware-prices',
                name: 'hardware-prices',
                component: () => import('@/views/HardwarePricesView.vue'),
                meta: { requiresAuth: true, title: '硬件价格' }
            },
            {
                path: 'hardware-analytics',
                name: 'hardware-analytics',
                component: () => import('@/views/HardwareAnalyticsView.vue'),
                meta: { requiresAuth: true, title: '价格分析' }
            },
            {
                path: 'draft-settlements',
                name: 'draft-settlements',
                component: () => import('@/views/DraftSettlementsView.vue'),
                // ✅ ADMIN 和 USER 都可以访问
                meta: { requiresAuth: true, title: '待结账', roles: ['ADMIN', 'USER'] }
            },
            {
                path: 'settlements',
                name: 'settlements',
                component: () => import('@/views/SettlementsView.vue'),
                // ✅ ADMIN 和 USER 都可以访问
                meta: { requiresAuth: true, title: '结账管理', roles: ['ADMIN', 'USER'] }
            },
            {
                path: 'submission-logs',
                name: 'submission-logs',
                component: () => import('@/views/SubmissionLogsView.vue'),
                // ✅ ADMIN 和 USER 都可以访问
                meta: { requiresAuth: true, title: '提交记录', roles: ['ADMIN', 'USER'] }
            },
            {
                path: 'users',
                name: 'users',
                component: () => import('@/views/UsersView.vue'),
                // ✅ 只有管理员可以访问
                meta: { requiresAuth: true, roles: ['ADMIN'], title: '用户管理' }
            },
            {
                path: 'logs',
                name: 'logs',
                component: () => import('@/views/LogsView.vue'),
                // ✅ 只有管理员可以访问
                meta: { requiresAuth: true, roles: ['ADMIN'], title: '操作日志' }
            }
        ]
    },
    {
        // 兜底路由, 访问地址不存在, 重定向到 /dashboard
        path: '/:pathMatch(.*)*',
        redirect: '/dashboard'
    }
];
const scrollPositions = new Map();
// 创建 Router 实例, 控制跳转 监听变化, 使用导航卫士💂
const router = createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition)
            return savedPosition;
        const pos = scrollPositions.get(to.path);
        if (typeof pos === 'number') {
            return { left: 0, top: pos };
        }
        return { left: 0, top: 0 };
    }
});
// 鉴权、避免已登录用户再进登录页、按角色限制访问
// 全局路由守卫（核心权限控制）
router.beforeEach((to, from, next) => {
    try {
        if (typeof window !== 'undefined') {
            scrollPositions.set(from.path, window.scrollY || window.pageYOffset || 0);
        }
    }
    catch { }
    const auth = useAuthStore(); // 获取登陆状态 是否登陆 角色 token
    // 是否需要登录：读取目标路由的 meta.requiresAuth
    const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
    // 未登录跳转登录页
    if (requiresAuth && !auth.isAuthenticated) {
        next({ name: 'login', query: { redirect: to.fullPath } });
        return;
    }
    // 已登录访问 /login 不允许，重定向到 dashboard
    if (to.name === 'login' && auth.isAuthenticated) {
        next({ path: '/dashboard' });
        return;
    }
    // 按角色权限控制访问（只对带 roles 的路由生效）
    const roles = to.meta.roles;
    const userRole = auth.user?.role ?? '';
    // 调试信息 - 强制输出
    console.log('=== 路由守卫开始 ===');
    console.log('[路由守卫] 目标路由:', to.path, to.name);
    console.log('[路由守卫] 完整 meta:', to.meta);
    console.log('[路由守卫] 需要角色:', roles);
    console.log('[路由守卫] 用户角色:', userRole);
    console.log('[路由守卫] 完整用户信息:', JSON.stringify(auth.user));
    console.log('[路由守卫] 角色匹配检查:', roles ? `roles.includes('${userRole}') = ${roles.includes(userRole)}` : '无角色限制');
    if (roles && !roles.includes(userRole)) {
        console.error('[路由守卫] ❌ 权限不足！重定向到 dashboard');
        console.error('[路由守卫] 需要的角色:', roles);
        console.error('[路由守卫] 用户的角色:', userRole);
        next({ path: '/dashboard' });
        return;
    }
    console.log('[路由守卫] ✅ 权限检查通过，允许访问');
    next();
});
export default router;
