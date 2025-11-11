<template>
  <el-menu
    :default-active="activeMenu"
    :collapse="collapse"
    background-color="var(--sidebar-bg)"
    text-color="#cbd5f5"
    active-text-color="var(--primary-color)"
    @select="handleSelect"
  >
    <el-menu-item v-for="item in items" :key="item.path" :index="item.path">
      <el-icon><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup lang="ts">
import type { NavItem } from '@/types/navigation';

const props = withDefaults(defineProps<{
  items: NavItem[];
  activeMenu: string;
  collapse?: boolean;
}>(), {
  collapse: false
});

const emit = defineEmits<{ (e: 'select', index: string): void }>();

const handleSelect = (index: string) => emit('select', index);
</script>
