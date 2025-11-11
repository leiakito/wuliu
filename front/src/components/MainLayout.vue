<template>
  <el-container class="layout" :class="{ 'layout--mobile': isMobile }">
    <el-aside v-if="!isMobile" width="230" class="layout-aside">
      <div class="logo">物流对账平台</div>
      <SidebarMenu :items="menuItems" :active-menu="activeMenu" @select="handleSelect" />
    </el-aside>

    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      size="240px"
      :with-header="false"
      custom-class="menu-drawer"
    >
      <div class="logo logo--drawer">物流对账平台</div>
      <SidebarMenu :items="menuItems" :active-menu="activeMenu" @select="handleSelect" />
    </el-drawer>

    <el-container>
      <el-header class="layout-header" :class="{ 'is-mobile': isMobile }">
        <div class="header-left">
          <el-button v-if="isMobile" text class="menu-trigger" @click="drawerVisible = true">
            <el-icon><Menu /></el-icon>
          </el-button>
          <div>
            <div class="header-title">{{ currentTitle }}</div>
            <p class="header-sub">物流单号、结账与权限一站式管理</p>
          </div>
        </div>
        <div class="header-actions">
          <el-tag size="small" effect="dark" type="info">{{ roleLabel }}</el-tag>
          <el-dropdown>
            <span class="el-dropdown-link">
              {{ auth.user?.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goProfile">个人信息</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Document, House, Tickets, User, Memo, ArrowDown, Menu } from '@element-plus/icons-vue';
import { useAuthStore } from '@/store/auth';
import SidebarMenu from '@/components/SidebarMenu.vue';
import type { NavItem } from '@/types/navigation';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const isMobile = ref(false);
const drawerVisible = ref(false);

const baseMenus: NavItem[] = [
  { label: '仪表盘', path: '/dashboard', icon: House },
  { label: '物流单号', path: '/orders', icon: Document },
  { label: '硬件价格', path: '/hardware-prices', icon: Tickets },
  { label: '结账管理', path: '/settlements', icon: Tickets, roles: ['ADMIN'] },
  { label: '用户管理', path: '/users', icon: User, roles: ['ADMIN'] },
  { label: '操作日志', path: '/logs', icon: Memo, roles: ['ADMIN'] }
];

const menuItems = computed(() =>
  baseMenus.filter(item => !item.roles || item.roles.includes(auth.user?.role ?? ''))
);

const activeMenu = computed(() => {
  const matched = baseMenus.find(item => route.path.startsWith(item.path));
  return matched ? matched.path : '/dashboard';
});

const currentTitle = computed(() => route.meta.title ?? '仪表盘');

const roleLabel = computed(() => {
  if (auth.user?.role === 'ADMIN') return '管理员';
  if (auth.user?.role === 'USER') return '用户';
  return '访客';
});

const handleSelect = (index: string) => {
  if (index !== route.path) {
    router.push(index);
  }
};

const logout = () => {
  auth.logout();
  router.replace({ name: 'login' });
};

const goProfile = () => {
  router.push({ path: '/dashboard', query: { focus: 'profile' } });
};

const handleResize = () => {
  isMobile.value = window.innerWidth <= 992;
};

onMounted(() => {
  handleResize();
  window.addEventListener('resize', handleResize);
  if (!auth.user && auth.token) {
    auth.fetchProfile();
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
});

watch(
  () => route.fullPath,
  () => {
    drawerVisible.value = false;
  }
);
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: var(--page-bg);
}

.layout-aside {
  background: var(--sidebar-bg);
  color: #fff;
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.layout-main {
  background: var(--page-bg);
}

.logo {
  font-size: 18px;
  font-weight: 600;
  text-align: center;
  padding: 28px 0;
  letter-spacing: 2px;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 0 24px;
  border-bottom: 1px solid #f1f4f8;
}

.layout-header.is-mobile {
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e2a45;
}

.header-sub {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.menu-trigger {
  font-size: 20px;
  color: var(--primary-color);
}

.menu-drawer {
  background: var(--sidebar-bg);
  color: #fff;
}

.logo--drawer {
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  margin-bottom: 12px;
}

@media (max-width: 992px) {
  .layout--mobile .layout-aside {
    display: none;
  }
}
</style>
