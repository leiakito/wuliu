import { defineStore } from 'pinia'; //全局状态仓库
import { computed, ref } from 'vue'; //响应式变量 定义计算属性
import { getProfile } from '@/api/auth'; //获取当前登录的个人信息
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/constants/storage';
//加载用户信息
function loadUser() {
    try {
        //读取本地缓存的用户信息字符串
        const cached = localStorage.getItem(USER_STORAGE_KEY);
        const user = cached ? JSON.parse(cached) : null;
        console.log('[Auth Store] loadUser 从缓存加载:', user);
        return user;
    }
    catch (error) {
        console.error('Failed to parse cached user', error);
        return null;
    }
}
export const useAuthStore = defineStore('auth', () => {
    const token = ref(localStorage.getItem(TOKEN_STORAGE_KEY) ?? '');
    const user = ref(loadUser());
    const profileLoading = ref(false);
    const isAuthenticated = computed(() => Boolean(token.value));
    const setToken = (value) => {
        token.value = value;
        if (value) {
            localStorage.setItem(TOKEN_STORAGE_KEY, value);
        }
        else {
            localStorage.removeItem(TOKEN_STORAGE_KEY);
        }
    };
    const setUser = (value) => {
        if (value && !value.role) {
            value.role = 'USER';
        }
        console.log('[Auth Store] setUser 被调用，用户信息:', value);
        user.value = value;
        if (value) {
            localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(value));
        }
        else {
            localStorage.removeItem(USER_STORAGE_KEY);
        }
    };
    const logout = () => {
        setToken('');
        setUser(null);
    };
    const fetchProfile = async () => {
        if (!token.value)
            return;
        profileLoading.value = true;
        try {
            const response = await getProfile();
            console.log('[Auth Store] fetchProfile 响应:', response);
            setUser(response.data);
        }
        finally {
            profileLoading.value = false;
        }
    };
    return {
        token,
        user,
        isAuthenticated,
        profileLoading,
        setToken,
        setUser,
        logout,
        fetchProfile
    };
});
