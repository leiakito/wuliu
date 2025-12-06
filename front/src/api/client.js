import axios from 'axios';
import { ElMessage } from 'element-plus';
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/constants/storage';
const apiClient = axios.create({
    baseURL: '/api',
    timeout: 120000
});
//请求加Token‘ Authorization: <your-token>
apiClient.interceptors.request.use(config => {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (token) {
        config.headers = config.headers ?? {};
        config.headers.Authorization = token;
    }
    return config;
});
//判断是否是文件流不处理json
apiClient.interceptors.response.use(response => {
    const contentType = response.headers['content-type'];
    if (contentType && contentType.includes('application/octet-stream')) {
        return response;
    }
    const payload = response.data;
    if (payload?.success) {
        return payload.data;
    }
    const message = payload?.message ?? '请求失败';
    ElMessage.error(message);
    return Promise.reject(new Error(message));
}, error => {
    if (error.response?.status === 401) {
        ElMessage.error('登录已过期，请重新登录');
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        localStorage.removeItem(USER_STORAGE_KEY);
        window.location.href = '/login';
    }
    else {
        const errorMessage = error.response?.data?.message ?? '网络错误';
        // 乐观锁冲突由各页面自己处理显示更明确的提示，这里不重复显示
        if (!errorMessage.includes('已被') && !errorMessage.includes('修改')) {
            ElMessage.error(errorMessage);
        }
    }
    return Promise.reject(error);
});
export default apiClient;
