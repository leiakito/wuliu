import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { getProfile } from '@/api/auth';
import type { UserProfile } from '@/types/models';
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/constants/storage';

function loadUser(): UserProfile | null {
  try {
    const cached = localStorage.getItem(USER_STORAGE_KEY);
    return cached ? (JSON.parse(cached) as UserProfile) : null;
  } catch (error) {
    console.error('Failed to parse cached user', error);
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_STORAGE_KEY) ?? '');
  const user = ref<UserProfile | null>(loadUser());
  const profileLoading = ref(false);

  const isAuthenticated = computed(() => Boolean(token.value));

  const setToken = (value: string) => {
    token.value = value;
    if (value) {
      localStorage.setItem(TOKEN_STORAGE_KEY, value);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  };

  const setUser = (value: UserProfile | null) => {
    user.value = value;
    if (value) {
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(value));
    } else {
      localStorage.removeItem(USER_STORAGE_KEY);
    }
  };

  const logout = () => {
    setToken('');
    setUser(null);
  };

  const fetchProfile = async () => {
    if (!token.value) return;
    profileLoading.value = true;
    try {
      const response = await getProfile();
      setUser(response.data);
    } finally {
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
