<template>
  <router-view />
</template>

<script setup lang="ts">
import { watch } from 'vue';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();

</script>
