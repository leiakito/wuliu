<template>
  <div>
    <router-view />
    <el-dialog v-model="announcementVisible" title="系统公告" width="520px" append-to-body>
      <template v-if="latestAnnouncement">
        <h3 class="announcement-title">{{ latestAnnouncement.title }}</h3>
        <p class="announcement-content">{{ latestAnnouncement.content }}</p>
        <small class="announcement-meta">发布时间：{{ formatDate(latestAnnouncement.createdAt) }}</small>
      </template>
      <template #footer>
        <el-button type="primary" @click="ackAnnouncement">我已知晓</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useAuthStore } from '@/store/auth';
import { enableCrisp, disableCrisp } from '@/utils/crisp';
import { fetchLatestAnnouncement } from '@/api/announcements';
import type { Announcement } from '@/types/models';

const ANNOUNCEMENT_ACK_KEY = 'announcement_ack_id';

const auth = useAuthStore();
const latestAnnouncement = ref<Announcement | null>(null);
const announcementVisible = ref(false);
const checking = ref(false);

const loadAnnouncement = async () => {
  if (!auth.isAuthenticated || checking.value) return;
  checking.value = true;
  try {
    const announcement = await fetchLatestAnnouncement();
    if (!announcement) {
      announcementVisible.value = false;
      return;
    }
    const acked = Number(localStorage.getItem(ANNOUNCEMENT_ACK_KEY) ?? '0');
    if (announcement.id <= acked) {
      announcementVisible.value = false;
      return;
    }
    latestAnnouncement.value = announcement;
    announcementVisible.value = true;
  } finally {
    checking.value = false;
  }
};

const ackAnnouncement = () => {
  if (latestAnnouncement.value) {
    localStorage.setItem(ANNOUNCEMENT_ACK_KEY, String(latestAnnouncement.value.id));
  }
  announcementVisible.value = false;
};

const formatDate = (value: string) => value.replace('T', ' ').slice(0, 19);

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

watch(
  () => auth.isAuthenticated,
  value => {
    if (value) {
      loadAnnouncement();
    } else {
      announcementVisible.value = false;
    }
  },
  { immediate: true }
);
</script>

<style scoped>
.announcement-title {
  margin: 0 0 8px;
  font-size: 18px;
}

.announcement-content {
  white-space: pre-wrap;
  margin: 0 0 8px;
}

.announcement-meta {
  color: var(--text-muted);
}
</style>
