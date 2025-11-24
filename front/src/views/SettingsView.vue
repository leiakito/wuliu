<template>
  <div class="page settings-page">
    <div class="page-header">
      <div>
        <h2>系统设置</h2>
        <p class="sub">管理系统配置、应用参数和偏好设置</p>
      </div>
    </div>

    <el-row :gutter="20">
      <!-- 左侧设置菜单 -->
      <el-col :xs="24" :sm="6" :md="5">
        <el-card class="settings-menu">
          <el-menu :default-active="activeTab" @select="handleTabChange">
            <el-menu-item index="general">
              <el-icon><Setting /></el-icon>
              <span>常规设置</span>
            </el-menu-item>
            <el-menu-item index="system">
              <el-icon><Tools /></el-icon>
              <span>系统参数</span>
            </el-menu-item>
            <el-menu-item index="notifications">
              <el-icon><Bell /></el-icon>
              <span>通知设置</span>
            </el-menu-item>
            <el-menu-item index="security">
              <el-icon><Lock /></el-icon>
              <span>安全设置</span>
            </el-menu-item>
            <el-menu-item index="about">
              <el-icon><InfoFilled /></el-icon>
              <span>关于系统</span>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>

      <!-- 右侧设置内容 -->
      <el-col :xs="24" :sm="18" :md="19">
        <!-- 常规设置 -->
        <el-card v-if="activeTab === 'general'" class="settings-content">
          <template #header>
            <div class="card-header">
              <span>常规设置</span>
              <el-button type="primary" size="small" @click="saveGeneralSettings">保存设置</el-button>
            </div>
          </template>

          <el-form :model="generalForm" label-width="140px" label-position="left">
            <el-form-item label="系统名称">
              <el-input v-model="generalForm.systemName" placeholder="物流对账平台" />
            </el-form-item>

            <el-form-item label="公司名称">
              <el-input v-model="generalForm.companyName" placeholder="输入公司名称" />
            </el-form-item>

            <el-form-item label="时区设置">
              <el-select v-model="generalForm.timezone" placeholder="选择时区">
                <el-option label="中国标准时间 (GMT+8)" value="Asia/Shanghai" />
                <el-option label="美国东部时间 (GMT-5)" value="America/New_York" />
                <el-option label="欧洲中部时间 (GMT+1)" value="Europe/Paris" />
                <el-option label="日本标准时间 (GMT+9)" value="Asia/Tokyo" />
              </el-select>
            </el-form-item>

            <el-form-item label="语言">
              <el-select v-model="generalForm.language" placeholder="选择语言">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="English" value="en-US" />
              </el-select>
            </el-form-item>

            <el-form-item label="默认货币">
              <el-select v-model="generalForm.currency" placeholder="选择货币">
                <el-option label="人民币 (CNY)" value="CNY" />
                <el-option label="美元 (USD)" value="USD" />
                <el-option label="欧元 (EUR)" value="EUR" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 系统参数 -->
        <el-card v-if="activeTab === 'system'" class="settings-content">
          <template #header>
            <div class="card-header">
              <span>系统参数</span>
              <el-button type="primary" size="small" @click="saveSystemSettings">保存设置</el-button>
            </div>
          </template>

          <el-form :model="systemForm" label-width="160px" label-position="left">
            <el-form-item label="自动结算周期">
              <el-select v-model="systemForm.settlementCycle">
                <el-option label="每月" value="monthly" />
                <el-option label="每周" value="weekly" />
                <el-option label="手动" value="manual" />
              </el-select>
            </el-form-item>

            <el-form-item label="订单保留天数">
              <el-input-number v-model="systemForm.orderRetentionDays" :min="30" :max="3650" />
              <span class="form-tip">订单数据保留时间（天）</span>
            </el-form-item>

            <el-form-item label="每页显示数量">
              <el-input-number v-model="systemForm.pageSize" :min="10" :max="100" :step="10" />
              <span class="form-tip">表格每页默认显示条数</span>
            </el-form-item>

            <el-form-item label="文件上传大小限制">
              <el-input-number v-model="systemForm.maxUploadSize" :min="1" :max="100" />
              <span class="form-tip">MB</span>
            </el-form-item>

            <el-form-item label="启用自动备份">
              <el-switch v-model="systemForm.autoBackup" />
              <span class="form-tip">每日凌晨自动备份数据库</span>
            </el-form-item>

            <el-form-item label="启用操作日志">
              <el-switch v-model="systemForm.enableOperationLog" />
              <span class="form-tip">记录用户所有操作日志</span>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 通知设置 -->
        <el-card v-if="activeTab === 'notifications'" class="settings-content">
          <template #header>
            <div class="card-header">
              <span>通知设置</span>
              <el-button type="primary" size="small" @click="saveNotificationSettings">保存设置</el-button>
            </div>
          </template>

          <el-form :model="notificationForm" label-width="180px" label-position="left">
            <el-form-item label="启用邮件通知">
              <el-switch v-model="notificationForm.emailEnabled" />
            </el-form-item>

            <el-form-item v-if="notificationForm.emailEnabled" label="SMTP服务器">
              <el-input v-model="notificationForm.smtpHost" placeholder="smtp.example.com" />
            </el-form-item>

            <el-form-item v-if="notificationForm.emailEnabled" label="SMTP端口">
              <el-input-number v-model="notificationForm.smtpPort" :min="1" :max="65535" />
            </el-form-item>

            <el-form-item v-if="notificationForm.emailEnabled" label="发件人邮箱">
              <el-input v-model="notificationForm.senderEmail" placeholder="noreply@example.com" />
            </el-form-item>

            <el-divider />

            <el-form-item label="新订单通知">
              <el-switch v-model="notificationForm.notifyNewOrder" />
            </el-form-item>

            <el-form-item label="结算完成通知">
              <el-switch v-model="notificationForm.notifySettlement" />
            </el-form-item>

            <el-form-item label="异常订单通知">
              <el-switch v-model="notificationForm.notifyException" />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 安全设置 -->
        <el-card v-if="activeTab === 'security'" class="settings-content">
          <template #header>
            <div class="card-header">
              <span>安全设置</span>
              <el-button type="primary" size="small" @click="saveSecuritySettings">保存设置</el-button>
            </div>
          </template>

          <el-form :model="securityForm" label-width="180px" label-position="left">
            <el-form-item label="密码最小长度">
              <el-input-number v-model="securityForm.minPasswordLength" :min="6" :max="20" />
            </el-form-item>

            <el-form-item label="密码过期天数">
              <el-input-number v-model="securityForm.passwordExpireDays" :min="0" :max="365" />
              <span class="form-tip">0表示永不过期</span>
            </el-form-item>

            <el-form-item label="登录失败锁定次数">
              <el-input-number v-model="securityForm.maxLoginAttempts" :min="3" :max="10" />
            </el-form-item>

            <el-form-item label="会话超时时间">
              <el-input-number v-model="securityForm.sessionTimeout" :min="15" :max="1440" :step="15" />
              <span class="form-tip">分钟</span>
            </el-form-item>

            <el-form-item label="启用双因素认证">
              <el-switch v-model="securityForm.enable2FA" />
            </el-form-item>

            <el-form-item label="强制HTTPS">
              <el-switch v-model="securityForm.forceHttps" />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 关于系统 -->
        <el-card v-if="activeTab === 'about'" class="settings-content about-content">
          <div class="about-section">
            <el-icon class="about-icon" :size="64"><Monitor /></el-icon>
            <h3>物流对账平台</h3>
            <p class="version">版本 2.0.0</p>
          </div>

          <el-divider />

          <el-descriptions :column="1" border>
            <el-descriptions-item label="系统版本">2.0.0</el-descriptions-item>
            <el-descriptions-item label="构建时间">2025-11-19</el-descriptions-item>
            <el-descriptions-item label="后端框架">Spring Boot 3.x</el-descriptions-item>
            <el-descriptions-item label="前端框架">Vue 3 + Element Plus</el-descriptions-item>
            <el-descriptions-item label="数据库">MySQL 8.0</el-descriptions-item>
            <el-descriptions-item label="许可证">MIT License</el-descriptions-item>
          </el-descriptions>

          <div class="about-footer">
            <el-button type="primary" @click="checkUpdate">检查更新</el-button>
            <el-button @click="viewLogs">查看更新日志</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Setting,
  Tools,
  Bell,
  Lock,
  InfoFilled,
  Monitor
} from '@element-plus/icons-vue';

const activeTab = ref('general');

const generalForm = reactive({
  systemName: '物流对账平台',
  companyName: '',
  timezone: 'Asia/Shanghai',
  language: 'zh-CN',
  currency: 'CNY'
});

const systemForm = reactive({
  settlementCycle: 'monthly',
  orderRetentionDays: 365,
  pageSize: 20,
  maxUploadSize: 10,
  autoBackup: true,
  enableOperationLog: true
});

const notificationForm = reactive({
  emailEnabled: false,
  smtpHost: '',
  smtpPort: 587,
  senderEmail: '',
  notifyNewOrder: true,
  notifySettlement: true,
  notifyException: true
});

const securityForm = reactive({
  minPasswordLength: 8,
  passwordExpireDays: 90,
  maxLoginAttempts: 5,
  sessionTimeout: 120,
  enable2FA: false,
  forceHttps: true
});

const handleTabChange = (key: string) => {
  activeTab.value = key;
};

const saveGeneralSettings = () => {
  ElMessage.success('常规设置已保存');
};

const saveSystemSettings = () => {
  ElMessage.success('系统参数已保存');
};

const saveNotificationSettings = () => {
  ElMessage.success('通知设置已保存');
};

const saveSecuritySettings = () => {
  ElMessage.success('安全设置已保存');
};

const checkUpdate = () => {
  ElMessage.info('当前已是最新版本');
};

const viewLogs = () => {
  ElMessage.info('查看更新日志功能开发中');
};
</script>

<style scoped>
.settings-page {
  max-width: 1400px;
  margin: 0 auto;
}

.settings-menu {
  position: sticky;
  top: 20px;
}

.settings-menu :deep(.el-menu) {
  border: none;
}

.settings-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  margin-bottom: 4px;
  border-radius: 6px;
}

.settings-content {
  min-height: 500px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}

.about-content {
  text-align: center;
}

.about-section {
  padding: 40px 0;
}

.about-icon {
  color: #409eff;
  margin-bottom: 20px;
}

.about-section h3 {
  margin: 12px 0;
  font-size: 24px;
  color: #303133;
}

.version {
  color: #909399;
  font-size: 14px;
}

.about-footer {
  margin-top: 30px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

@media (max-width: 768px) {
  .settings-menu {
    position: static;
    margin-bottom: 16px;
  }
}
</style>
