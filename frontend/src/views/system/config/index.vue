<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { configApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import type { ConfigRow } from '@/types/system'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<ConfigRow>(configApi.list, { keyword: '' })

const dialogVisible = ref(false)
const current = ref<ConfigRow | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ value: '' })
const rules: FormRules = {
  value: [{ required: true, message: '请输入参数值', trigger: 'blur' }]
}

function openEdit(row: ConfigRow): void {
  current.value = row
  form.value = row.value ?? ''
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submit(): Promise<void> {
  if (!current.value || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await configApi.update(current.value.id, form.value)
    ElMessage.success('参数值修改成功')
    dialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">参数配置</span>
          <span class="page-toolbar-desc">系统运行参数；内置参数仅允许修改参数值，参数键与名称不可变更</span>
        </div>
        <div class="page-toolbar-actions">
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="参数名称 / 参数键"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无参数配置">
        <el-table-column prop="name" label="参数名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="key" label="参数键" min-width="200" show-overflow-tooltip />
        <el-table-column prop="value" label="参数值" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.builtin ? 'warning' : 'info'" size="small">
              {{ row.builtin ? '内置参数' : '业务参数' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-perm="'system:config:edit'" @click="openEdit(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="修改参数值" width="520px" destroy-on-close>
      <el-alert
        v-if="current?.builtin"
        type="warning"
        :closable="false"
        show-icon
        title="内置参数"
        description="该参数由系统内置，仅允许修改参数值，修改后将影响相关功能行为，请谨慎操作。"
        class="dialog-alert"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="参数名称">
          <el-input :model-value="current?.name" disabled />
        </el-form-item>
        <el-form-item label="参数键">
          <el-input :model-value="current?.key" disabled />
        </el-form-item>
        <el-form-item label="参数值" prop="value">
          <el-input v-model="form.value" type="textarea" :rows="3" maxlength="500" placeholder="请输入参数值" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input :model-value="current?.remark" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
}

.page-toolbar-heading {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.page-toolbar-desc {
  font-size: 12px;
  color: #8a94a6;
}

.page-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.toolbar-input {
  width: 220px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.dialog-alert {
  margin-bottom: 16px;
}
</style>
