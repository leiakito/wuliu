<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>系统公告</h2>
        <p class="sub">查看管理员发布的最新通知</p>
      </div>
      <el-button v-if="isAdmin" type="primary" @click="formVisible = true">发布公告</el-button>
    </div>

    <el-card v-loading="loading" class="table-card">
      <template v-if="announcements.length">
        <el-timeline>
          <el-timeline-item
            v-for="item in announcements"
            :key="item.id"
            :timestamp="formatDate(item.createdAt)"
          >
            <h3>{{ item.title }}</h3>
            <p class="content">{{ item.content }}</p>
            <small class="meta">发布人：{{ item.createdBy ?? '系统' }}</small>
          </el-timeline-item>
        </el-timeline>
      </template>
      <div v-else class="empty">暂无公告</div>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[5, 10, 20]"
        style="margin-top: 12px; justify-content: flex-end"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="formVisible" title="发布公告" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model.trim="form.title" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model.trim="form.content"
            type="textarea"
            :rows="6"
            placeholder="请输入公告内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fetchAnnouncements, createAnnouncement } from '@/api/announcements';
import type { Announcement, AnnouncementCreateRequest } from '@/types/models';
import { useAuthStore } from '@/store/auth';

const auth = useAuthStore();
const isAdmin = computed(() => auth.user?.role === 'ADMIN');

const announcements = ref<Announcement[]>([]);
const loading = ref(false);
const formVisible = ref(false);
const submitting = ref(false);
const pagination = reactive({ page: 1, size: 10, total: 0 });

const form = reactive<AnnouncementCreateRequest>({
  title: '',
  content: ''
});

const loadData = async () => {
  loading.value = true;
  try {
    const response = await fetchAnnouncements({ page: pagination.page, size: pagination.size });
    announcements.value = response.records;
    pagination.total = response.total;
  } finally {
    loading.value = false;
  }
};

const handlePageChange = (page: number) => {
  pagination.page = page;
  loadData();
};

const handleSizeChange = (size: number) => {
  pagination.size = size;
  pagination.page = 1;
  loadData();
};

const handleSubmit = async () => {
  if (!form.title || !form.content) {
    ElMessage.warning('请填写完整的公告内容');
    return;
  }
  submitting.value = true;
  try {
    await createAnnouncement(form);
    ElMessage.success('公告已发布');
    formVisible.value = false;
    form.title = '';
    form.content = '';
    pagination.page = 1;
    loadData();
  } finally {
    submitting.value = false;
  }
};

const formatDate = (value: string) => value.replace('T', ' ').slice(0, 19);

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.table-card {
  min-height: 360px;
}

.content {
  white-space: pre-wrap;
  margin: 8px 0 4px;
}

.meta {
  color: var(--text-muted);
}

.empty {
  padding: 32px 0;
  text-align: center;
  color: var(--text-muted);
}
</style>
