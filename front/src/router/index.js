import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import MainLayout from '@/components/MainLayout.vue';
//路由系统
const routes = [
    {
        //不需要登陆,访问/login 链接到@/views/LoginView.vue
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
                meta: { requiresAuth: true, title: '单号总查询' }
            },
            {
                path: 'orders',
                name: 'orders', //权限限制页面,只要roles 管理员才可以访问
                component: () => import('@/views/OrdersView.vue'),
                meta: { requiresAuth: true, title: '物流单号', roles: ['ADMIN'], keepAlive: true }
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
                path: 'settlements',
                name: 'settlements',
                component: () => import('@/views/SettlementsView.vue'),
                meta: { requiresAuth: true, title: '结账管理', roles: ['ADMIN'] }
            },
            {
                path: 'submission-logs',
                name: 'submission-logs',
                component: () => import('@/views/SubmissionLogsView.vue'),
                meta: { requiresAuth: true, title: '提交记录', roles: ['ADMIN'] }
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
        path: '/:pathMatch(.*)*', //兜底路由,访问地址不存在,重定向到/dashborad
        redirect: '/dashboard'
    }
];
const scrollPositions = new Map();
//创建Router实例, 控制跳转 监听变化,使用导航卫士💂
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
//鉴权、避免已登录用户再进登录页、按角色限制访问
//全局路由守卫（核心权限控制）
//每次路由跳转前都会执行
router.beforeEach((to, from, next) => {
    try {
        if (typeof window !== 'undefined') {
            scrollPositions.set(from.path, window.scrollY || window.pageYOffset || 0);
        }
    }
    catch { }
    const auth = useAuthStore(); //获取登陆状态 是否登陆 角色 token 
    const requiresAuth = to.matched.some(record => record.meta.requiresAuth); //算是否需要登录：读取目标路由的 meta.requiresAuth
    //未登录跳转登录页
    if (requiresAuth && !auth.isAuthenticated) {
        next({ name: 'login', query: { redirect: to.fullPath } });
        return;
    }
    //已登录访问/login不允许/重定向到dashboard
    if (to.name === 'login' && auth.isAuthenticated) {
        next({ path: '/dashboard' });
        return;
    }
    //按角色权限控制访问 
    const roles = to.meta.roles;
    if (roles && !roles.includes(auth.user?.role ?? '')) {
        next({ path: '/dashboard' });
        return;
    }
    next();
});
export default router;
