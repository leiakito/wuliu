import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import MainLayout from '@/components/MainLayout.vue';
const routes = [
    {
        path: '/login',
        name: 'login',
        component: () => import('@/views/LoginView.vue')
    },
    {
        path: '/',
        component: MainLayout,
        meta: { requiresAuth: true },
        children: [
            { path: '', redirect: '/dashboard' },
            {
                path: 'dashboard',
                name: 'dashboard',
                component: () => import('@/views/DashboardView.vue'),
                meta: { requiresAuth: true, title: '仪表盘' }
            },
            {
                path: 'orders',
                name: 'orders',
                component: () => import('@/views/OrdersView.vue'),
                meta: { requiresAuth: true, title: '物流单号' }
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
                path: 'settlements',
                name: 'settlements',
                component: () => import('@/views/SettlementsView.vue'),
                meta: { requiresAuth: true, title: '结账管理', roles: ['ADMIN'] }
            },
            {
                path: 'users',
                name: 'users',
                component: () => import('@/views/UsersView.vue'),
                meta: { requiresAuth: true, roles: ['ADMIN'], title: '用户管理' }
            },
            {
                path: 'logs',
                name: 'logs',
                component: () => import('@/views/LogsView.vue'),
                meta: { requiresAuth: true, roles: ['ADMIN'], title: '操作日志' }
            }
        ]
    },
    {
        path: '/:pathMatch(.*)*',
        redirect: '/dashboard'
    }
];
const router = createRouter({
    history: createWebHistory(),
    routes
});
router.beforeEach((to, _from, next) => {
    const auth = useAuthStore();
    const requiresAuth = to.matched.some(record => record.meta.requiresAuth);
    if (requiresAuth && !auth.isAuthenticated) {
        next({ name: 'login', query: { redirect: to.fullPath } });
        return;
    }
    if (to.name === 'login' && auth.isAuthenticated) {
        next({ path: '/dashboard' });
        return;
    }
    const roles = to.meta.roles;
    if (roles && !roles.includes(auth.user?.role ?? '')) {
        next({ path: '/dashboard' });
        return;
    }
    next();
});
export default router;
