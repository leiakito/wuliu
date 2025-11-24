<template>
  <router-view />
</template>

<script setup lang="ts">
import { watch } from 'vue';
import { useAuthStore } from '@/store/auth';
import { enableCrisp, disableCrisp } from '@/utils/crisp';

const auth = useAuthStore();

watch(
  () => auth.user?.role,
  role => {
    if (role === 'USER') {
      enableCrisp();
    } else {
      disableCrisp();
    }
  },
  { immediate: true }
);
</script>
