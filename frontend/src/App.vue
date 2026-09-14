<template>
  <div class="app">
    <!-- 登录视图 -->
    <Login v-if="view === 'login'" @logged-in="onLoggedIn" />

    <!-- 顶部导航 -->
    <header v-else class="navbar">
      <div class="nav-left">
        <h1 class="nav-title" @click="goClasses">文件共享服务器</h1>
      </div>
      <div class="nav-right">
        <span class="nav-user">{{ currentUser?.username }}</span>
        <span v-if="currentUser?.role === 'ADMIN'" class="badge">管理员</span>
        <button class="nav-btn" @click="logout">退出</button>
      </div>
    </header>

    <!-- 班级列表视图 -->
    <div v-if="view === 'classes'" class="container">
      <div class="section-header">
        <h2>我的班级</h2>
        <div class="header-actions">
          <button class="btn" @click="showJoinDialog = true">加入班级</button>
          <button v-if="currentUser?.role === 'ADMIN'" class="btn primary" @click="showCreateDialog = true">创建班级</button>
        </div>
      </div>
      <div v-if="classesLoading" class="empty">加载中...</div>
      <div v-else-if="!classes.length" class="empty">还没有加入任何班级，点击右上角"加入班级"开始使用。</div>
      <div v-else class="class-grid">
        <div v-for="cls in classes" :key="cls.id" class="class-card" @click="enterClass(cls)">
          <h3>{{ cls.name }}</h3>
          <div class="class-meta">
            <span>{{ cls.memberCount }} 名成员</span>
            <span class="invite-code">邀请码: {{ cls.inviteCode }}</span>
          </div>
        </div>
      </div>

      <!-- 管理员入口 -->
      <div v-if="currentUser?.role === 'ADMIN'" class="admin-section">
        <h2>管理</h2>
        <button class="btn" @click="view = 'admin'">用户与班级管理</button>
      </div>
    </div>

    <!-- 班级文件视图 -->
    <div v-if="view === 'files' && currentClass" class="container">
      <div class="section-header">
        <div class="breadcrumb">
          <button class="link-btn" @click="goClasses">班级</button>
          <span>/</span>
          <button class="link-btn" @click="go('/')">{{ currentClass.name }}</button>
          <template v-for="crumb in crumbs" :key="crumb.path">
            <span>/</span>
            <button class="link-btn" @click="go(crumb.path)">{{ crumb.name }}</button>
          </template>
        </div>
        <div class="header-actions">
          <input v-model="keyword" class="search-input" placeholder="搜索文件名" @keydown.enter="doSearch" />
          <button class="btn" @click="refresh">刷新</button>
          <button class="btn" @click="promptMkdir">新建目录</button>
          <label class="btn primary">
            上传文件
            <input type="file" multiple hidden @change="onSelectFiles" />
          </label>
        </div>
      </div>

      <div v-if="clipboard" class="clipboard-bar">
        <span>剪贴板：{{ clipboard.mode === 'copy' ? '复制' : '移动' }} {{ clipboard.paths.length }} 项</span>
        <button class="btn primary" @click="paste">粘贴到当前目录</button>
        <button class="btn" @click="clipboard = null">取消</button>
      </div>

      <div v-if="uploading" class="progress">
        <span>正在上传 {{ uploadingName }}</span>
        <i :style="{ width: uploadPercent + '%' }"></i>
      </div>

      <div v-if="loading" class="empty">正在读取目录...</div>
      <div v-else-if="!items.length" class="empty">这个目录还是空的，上传一些文件吧。</div>
      <table v-else class="file-table">
        <thead>
          <tr>
            <th class="col-check"><input type="checkbox" :checked="allSelected" @change="toggleSelectAll" /></th>
            <th class="sortable" @click="toggleSort('name')">名称 <em>{{ sortIcon('name') }}</em></th>
            <th class="sortable" @click="toggleSort('size')">大小 <em>{{ sortIcon('size') }}</em></th>
            <th class="sortable" @click="toggleSort('time')">修改时间 <em>{{ sortIcon('time') }}</em></th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in sortedItems" :key="item.path" :class="{ checked: selected.has(item.path) }">
            <td class="col-check"><input type="checkbox" :checked="selected.has(item.path)" @change="toggleSelect(item)" /></td>
            <td>
              <button class="name-btn" @click="openItem(item)">
                <span class="icon">{{ item.directory ? '📁' : fileIcon(item.name) }}</span>
                {{ item.name }}
              </button>
            </td>
            <td>{{ item.directory ? '—' : formatSize(item.size) }}</td>
            <td>{{ formatTime(item.modifiedAt) }}</td>
            <td class="row-actions">
              <button v-if="!item.directory" @click="download(item)">下载</button>
              <button v-if="!item.directory && canPreview(item.name)" @click="preview = item">预览</button>
              <button @click="clipCopy(item)">复制</button>
              <button @click="clipMove(item)">移动</button>
              <button @click="rename(item)">重命名</button>
              <button v-if="canDelete(item)" class="danger" @click="remove(item)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="selected.size > 0" class="batch-bar">
        <span>已选中 {{ selected.size }} 项</span>
        <button class="btn" @click="batchDownload">批量下载</button>
        <button class="btn" @click="clipCopySelected">批量复制</button>
        <button class="btn" @click="clipMoveSelected">批量移动</button>
        <button class="btn danger" @click="batchRemove">批量删除</button>
        <button class="btn" @click="selected = new Set()">取消选择</button>
      </div>
    </div>

    <!-- 管理员视图 -->
    <div v-if="view === 'admin'" class="container">
      <div class="section-header">
        <h2>管理面板</h2>
        <button class="btn" @click="view = 'classes'">返回班级列表</button>
      </div>

      <div class="admin-tabs">
        <button :class="{ active: adminTab === 'users' }" @click="adminTab = 'users'; loadAdminUsers()">用户管理</button>
        <button :class="{ active: adminTab === 'classes' }" @click="adminTab = 'classes'; loadAdminClasses()">班级管理</button>
      </div>

      <!-- 用户管理 -->
      <div v-if="adminTab === 'users'">
        <table class="file-table">
          <thead><tr><th>ID</th><th>用户名</th><th>角色</th><th>班级数</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="u in adminUsers" :key="u.id">
              <td>{{ u.id }}</td>
              <td>{{ u.username }}</td>
              <td><span class="role-tag" :class="u.role.toLowerCase()">{{ u.role }}</span></td>
              <td>{{ u.classCount }}</td>
              <td><button class="danger" @click="adminDeleteUser(u)" :disabled="u.id === currentUser.userId">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 班级管理 -->
      <div v-if="adminTab === 'classes'">
        <div class="admin-class-list">
          <div v-for="cls in adminClassList" :key="cls.id" class="admin-class-item">
            <div class="admin-class-info">
              <strong>{{ cls.name }}</strong>
              <span class="invite-code">邀请码: {{ cls.inviteCode }}</span>
              <span>{{ cls.memberCount }} 名成员</span>
            </div>
            <div class="admin-class-actions">
              <button class="btn" @click="viewClassMembers(cls)">查看成员</button>
              <button class="btn danger" @click="adminDeleteClass(cls)">删除班级</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗们 -->
    <div v-if="showCreateDialog" class="modal-overlay" @click.self="showCreateDialog = false">
      <div class="modal-card">
        <h3>创建班级</h3>
        <input v-model="newClassName" placeholder="班级名称" @keydown.enter="createClass" />
        <div class="modal-actions">
          <button class="btn primary" @click="createClass">创建</button>
          <button class="btn" @click="showCreateDialog = false">取消</button>
        </div>
      </div>
    </div>

    <div v-if="showJoinDialog" class="modal-overlay" @click.self="showJoinDialog = false">
      <div class="modal-card">
        <h3>加入班级</h3>
        <input v-model="joinCode" placeholder="请输入邀请码" @keydown.enter="joinClass" />
        <div class="modal-actions">
          <button class="btn primary" @click="joinClass">加入</button>
          <button class="btn" @click="showJoinDialog = false">取消</button>
        </div>
      </div>
    </div>

    <div v-if="preview" class="modal-overlay" @click.self="preview = null">
      <div class="modal-card large">
        <div class="modal-header">
          <h3>{{ preview.name }}</h3>
          <button class="btn" @click="preview = null">关闭</button>
        </div>
        <img v-if="isImage(preview.name)" :src="previewUrlRaw(preview.path)" alt="" />
        <iframe v-else :src="previewUrlRaw(preview.path)" title="preview"></iframe>
      </div>
    </div>

    <div v-if="duplicateInfo" class="modal-overlay" @click.self="duplicateInfo.resolve(false)">
      <div class="modal-card">
        <h3>发现重复文件</h3>
        <p>已存在同名文件 <strong>{{ duplicateInfo.fileName }}</strong>（{{ formatSize(duplicateInfo.file.size) }}），是否覆盖？</p>
        <div class="modal-actions">
          <button class="btn primary" @click="duplicateInfo.resolve(true)">覆盖</button>
          <button class="btn" @click="duplicateInfo.resolve(false)">跳过</button>
        </div>
      </div>
    </div>

    <div v-if="membersDialog" class="modal-overlay" @click.self="membersDialog = null">
      <div class="modal-card">
        <h3>{{ membersDialog.name }} - 成员列表</h3>
        <table class="file-table">
          <thead><tr><th>用户名</th><th>角色</th><th>加入时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="m in membersDialog.members" :key="m.userId">
              <td>{{ m.username }}</td>
              <td><span class="role-tag" :class="m.role?.toLowerCase()">{{ m.role }}</span></td>
              <td>{{ formatTime(m.joinedAt) }}</td>
              <td><button class="danger" @click="removeMember(m)">移除</button></td>
            </tr>
          </tbody>
        </table>
        <div class="modal-actions">
          <button class="btn" @click="membersDialog = null">关闭</button>
        </div>
      </div>
    </div>

    <p v-if="toast" class="toast">{{ toast }}</p>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, downloadUrl, previewUrl } from './api'
import Login from './Login.vue'

// Auth state
const currentUser = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const view = ref(currentUser.value ? 'classes' : 'login')

// Classes
const classes = ref([])
const classesLoading = ref(false)
const currentClass = ref(null)

// Files
const path = ref('/')
const items = ref([])
const loading = ref(false)
const keyword = ref('')

// Sort
const sortField = ref('name')
const sortAsc = ref(true)

// Selection
const selected = ref(new Set())

// Clipboard
const clipboard = ref(null)

// Upload
const uploading = ref(false)
const uploadingName = ref('')
const uploadPercent = ref(0)
const duplicateInfo = ref(null)

// Preview
const preview = ref(null)

// Dialogs
const showCreateDialog = ref(false)
const showJoinDialog = ref(false)
const newClassName = ref('')
const joinCode = ref('')
const membersDialog = ref(null)

// Admin
const adminTab = ref('users')
const adminUsers = ref([])
const adminClassList = ref([])

// Toast
const toast = ref('')

// Computed
const crumbs = computed(() => {
  const parts = path.value.split('/').filter(Boolean)
  const list = []
  let current = ''
  for (const part of parts) {
    current += `/${part}`
    list.push({ name: part, path: current })
  }
  return list
})

const sortedItems = computed(() => {
  const arr = [...items.value]
  const dir = sortAsc.value ? 1 : -1
  arr.sort((a, b) => {
    if (a.directory !== b.directory) return a.directory ? -1 : 1
    if (sortField.value === 'size') return (a.size - b.size) * dir
    if (sortField.value === 'time') {
      const ta = new Date(a.modifiedAt || 0).getTime()
      const tb = new Date(b.modifiedAt || 0).getTime()
      return (ta - tb) * dir
    }
    return a.name.localeCompare(b.name, 'zh-CN') * dir
  })
  return arr
})

const allSelected = computed(() => {
  return sortedItems.value.length > 0 && sortedItems.value.every(i => selected.value.has(i.path))
})

const selectedItems = computed(() => {
  return sortedItems.value.filter(i => selected.value.has(i.path))
})

// Helpers
function notify(message) {
  toast.value = message
  setTimeout(() => { if (toast.value === message) toast.value = '' }, 2400)
}

function formatSize(bytes) {
  const n = Number(bytes || 0)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const y = date.getFullYear()
  const m = date.getMonth() + 1
  const d = date.getDate()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}.${m}.${d} ${hh}:${mm}`
}

function fileIcon(name) {
  const ext = name.split('.').pop()?.toLowerCase()
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'].includes(ext)) return '🖼️'
  if (['mp4', 'mov', 'webm'].includes(ext)) return '🎬'
  if (['zip', 'rar', '7z'].includes(ext)) return '📦'
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(ext)) return '📄'
  return '📄'
}

function isImage(name) { return /\.(png|jpe?g|gif|webp|svg)$/i.test(name) }
function canPreview(name) { return isImage(name) || /\.(txt|md|pdf|json|csv)$/i.test(name) }
function canDelete(item) {
  // 管理员可以删除任何文件
  if (currentUser.value?.role === 'ADMIN') return true
  // 普通用户：显示删除按钮，后端会检查是否是自己的文件
  return true
}
function previewUrlRaw(p) { return previewUrl(currentClass.value.id, p) }

// Auth
function onLoggedIn(data) {
  currentUser.value = { userId: data.userId, username: data.username, role: data.role }
  view.value = 'classes'
  loadClasses()
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  currentUser.value = null
  view.value = 'login'
}

function onAuthExpired() {
  currentUser.value = null
  view.value = 'login'
}

// Classes
async function loadClasses() {
  classesLoading.value = true
  try {
    classes.value = await api.myClasses()
  } catch (err) {
    notify(err.message)
  } finally {
    classesLoading.value = false
  }
}

function enterClass(cls) {
  currentClass.value = cls
  path.value = '/'
  view.value = 'files'
  loadFiles()
}

function goClasses() {
  currentClass.value = null
  view.value = 'classes'
  loadClasses()
}

async function createClass() {
  if (!newClassName.value.trim()) return
  try {
    await api.createClass(newClassName.value.trim())
    showCreateDialog.value = false
    newClassName.value = ''
    notify('班级已创建')
    await loadClasses()
  } catch (err) { notify(err.message) }
}

async function joinClass() {
  if (!joinCode.value.trim()) return
  try {
    await api.joinClass(joinCode.value.trim())
    showJoinDialog.value = false
    joinCode.value = ''
    notify('已加入班级')
    await loadClasses()
  } catch (err) { notify(err.message) }
}

// Files
async function loadFiles() {
  if (!currentClass.value) return
  loading.value = true
  try {
    const data = await api.list(currentClass.value.id, path.value)
    items.value = data.items || []
    path.value = data.path || path.value
  } catch (err) { notify(err.message) }
  finally { loading.value = false }
}

function go(next) {
  path.value = next
  keyword.value = ''
  selected.value = new Set()
  loadFiles()
}

function refresh() { loadFiles() }

async function doSearch() {
  if (!keyword.value.trim()) return loadFiles()
  loading.value = true
  try {
    const data = await api.search(currentClass.value.id, keyword.value.trim())
    items.value = data.items || []
  } catch (err) { notify(err.message) }
  finally { loading.value = false }
}

function openItem(item) {
  if (item.directory) go(item.path)
  else if (canPreview(item.name)) preview.value = item
  else download(item)
}

function download(item) {
  window.open(downloadUrl(currentClass.value.id, item.path), '_blank')
}

async function promptMkdir() {
  const name = window.prompt('新目录名称')
  if (!name) return
  const next = path.value === '/' ? `/${name}` : `${path.value}/${name}`
  try {
    await api.mkdir(currentClass.value.id, next)
    notify('目录已创建')
    await loadFiles()
  } catch (err) { notify(err.message) }
}

async function rename(item) {
  const name = window.prompt('新的名称', item.name)
  if (!name || name === item.name) return
  try {
    await api.rename(currentClass.value.id, item.path, name)
    notify('已重命名')
    await loadFiles()
  } catch (err) { notify(err.message) }
}

async function remove(item) {
  if (!window.confirm(`确定删除「${item.name}」？`)) return
  try {
    await api.remove(currentClass.value.id, item.path)
    notify('已删除')
    await loadFiles()
  } catch (err) { notify(err.message) }
}

// Sort
function toggleSort(field) {
  if (sortField.value === field) sortAsc.value = !sortAsc.value
  else { sortField.value = field; sortAsc.value = true }
}
function sortIcon(field) {
  if (sortField.value !== field) return '↕'
  return sortAsc.value ? '↑' : '↓'
}

// Selection
function toggleSelect(item) {
  const s = new Set(selected.value)
  if (s.has(item.path)) s.delete(item.path); else s.add(item.path)
  selected.value = s
}
function toggleSelectAll() {
  if (allSelected.value) selected.value = new Set()
  else selected.value = new Set(sortedItems.value.map(i => i.path))
}

// Batch
async function batchRemove() {
  const paths = [...selected.value]
  if (!paths.length) return
  if (!window.confirm(`确定删除选中的 ${paths.length} 个文件/目录？`)) return
  try {
    const result = await api.batchDelete(currentClass.value.id, paths)
    notify(`已删除 ${result.success} 个，失败 ${result.failed} 个`)
    selected.value = new Set()
    await loadFiles()
  } catch (err) { notify(err.message) }
}

function batchDownload() {
  const items = selectedItems.value.filter(i => !i.directory)
  if (!items.length) { notify('请选择文件'); return }
  for (const item of items) window.open(downloadUrl(currentClass.value.id, item.path), '_blank')
}

// Clipboard
function clipCopy(item) { clipboard.value = { paths: [item.path], mode: 'copy' }; notify('已复制') }
function clipMove(item) { clipboard.value = { paths: [item.path], mode: 'move' }; notify('已剪切') }
function clipCopySelected() {
  const paths = [...selected.value]
  if (!paths.length) return
  clipboard.value = { paths, mode: 'copy' }; notify(`已复制 ${paths.length} 项`)
}
function clipMoveSelected() {
  const paths = [...selected.value]
  if (!paths.length) return
  clipboard.value = { paths, mode: 'move' }; notify(`已剪切 ${paths.length} 项`)
}
async function paste() {
  if (!clipboard.value) return
  const { paths, mode } = clipboard.value
  let success = 0, failed = 0
  for (const p of paths) {
    try {
      if (mode === 'copy') await api.copyFile(currentClass.value.id, p, path.value)
      else await api.moveFile(currentClass.value.id, p, path.value)
      success++
    } catch { failed++ }
  }
  notify(`${mode === 'copy' ? '复制' : '移动'}完成：成功 ${success}，失败 ${failed}`)
  clipboard.value = null
  await loadFiles()
}

// Upload
async function uploadFiles(fileList, overwrite = false) {
  const files = [...fileList]
  if (!files.length) return
  uploading.value = true
  try {
    for (const file of files) {
      uploadingName.value = file.name
      uploadPercent.value = 0
      const result = await api.upload(currentClass.value.id, path.value, file, (p) => { uploadPercent.value = p }, overwrite)
      if (!result.ok && result.message === 'duplicate') {
        const confirmed = await new Promise((resolve) => {
          duplicateInfo.value = { file: result.data, fileName: file.name, resolve: (v) => { duplicateInfo.value = null; resolve(v) } }
        })
        if (confirmed) await api.upload(currentClass.value.id, path.value, file, (p) => { uploadPercent.value = p }, true)
      }
    }
    notify(`已上传 ${files.length} 个文件`)
    await loadFiles()
  } catch (err) { notify(err.message) }
  finally { uploading.value = false }
}
function onSelectFiles(event) { uploadFiles(event.target.files); event.target.value = '' }

// Admin
async function loadAdminUsers() {
  try { adminUsers.value = await api.adminUsers() } catch (err) { notify(err.message) }
}
async function loadAdminClasses() {
  try { adminClassList.value = await api.adminClasses() } catch (err) { notify(err.message) }
}
async function adminDeleteUser(u) {
  if (!window.confirm(`确定删除用户「${u.username}」？`)) return
  try { await api.adminDeleteUser(u.id); notify('已删除'); await loadAdminUsers() } catch (err) { notify(err.message) }
}
async function adminDeleteClass(cls) {
  if (!window.confirm(`确定删除班级「${cls.name}」及其所有文件？`)) return
  try { await api.deleteClass(cls.id); notify('已删除'); await loadAdminClasses() } catch (err) { notify(err.message) }
}
async function viewClassMembers(cls) {
  try {
    const members = await api.classMembers(cls.id)
    membersDialog.value = { name: cls.name, members }
  } catch (err) { notify(err.message) }
}
async function removeMember(m) {
  if (!membersDialog.value) return
  if (!window.confirm(`确定移除「${m.username}」？`)) return
  try {
    await api.removeMember(membersDialog.value.members[0]?.classId || currentClass.value?.id, m.userId)
    notify('已移除')
    // Reload members
    const cls = adminClassList.value.find(c => c.name === membersDialog.value.name)
    if (cls) {
      const members = await api.classMembers(cls.id)
      membersDialog.value = { name: cls.name, members }
    }
  } catch (err) { notify(err.message) }
}

// Lifecycle
onMounted(() => {
  window.addEventListener('auth-expired', onAuthExpired)
  if (currentUser.value) loadClasses()
})
onUnmounted(() => {
  window.removeEventListener('auth-expired', onAuthExpired)
})
</script>

<style scoped>
.app { min-height: 100vh; }

.navbar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0 24px; height: 56px;
  background: var(--panel); border-bottom: 1px solid var(--line);
  box-shadow: var(--shadow); position: sticky; top: 0; z-index: 10;
}
.nav-title { margin: 0; font-size: 18px; font-weight: 600; cursor: pointer; color: var(--primary); }
.nav-right { display: flex; align-items: center; gap: 12px; }
.nav-user { color: var(--muted); font-size: 14px; }
.badge { background: var(--primary); color: #fff; padding: 2px 8px; border-radius: 10px; font-size: 12px; }
.nav-btn { border: 1px solid var(--line); background: none; padding: 6px 14px; border-radius: 6px; color: var(--muted); font-size: 13px; }
.nav-btn:hover { border-color: var(--danger); color: var(--danger); }

.container { max-width: 1200px; margin: 0 auto; padding: 24px 20px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.section-header h2 { margin: 0; font-size: 20px; font-weight: 600; }
.header-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

.breadcrumb { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.link-btn { border: none; background: none; color: var(--primary); padding: 4px 2px; font-size: 14px; cursor: pointer; }
.link-btn:hover { text-decoration: underline; }
.breadcrumb span { color: var(--muted); }

.class-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.class-card {
  background: var(--panel); border: 1px solid var(--line); border-radius: var(--radius);
  padding: 20px; cursor: pointer; transition: all 0.2s; box-shadow: var(--shadow);
}
.class-card:hover { border-color: var(--primary); transform: translateY(-2px); }
.class-card h3 { margin: 0 0 12px; font-size: 18px; }
.class-meta { display: flex; justify-content: space-between; font-size: 13px; color: var(--muted); }
.invite-code { font-family: monospace; background: var(--panel-2); padding: 2px 6px; border-radius: 4px; }

.admin-section { margin-top: 32px; padding-top: 24px; border-top: 1px solid var(--line); }

.btn {
  border: 1px solid var(--line); background: var(--panel); color: var(--text);
  border-radius: 6px; padding: 7px 14px; font-size: 13px; cursor: pointer; transition: all 0.2s;
}
.btn:hover { border-color: var(--primary); color: var(--primary); }
.btn.primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.btn.primary:hover { background: var(--primary-dark); }
.btn.danger, .danger { color: var(--danger); border-color: transparent; background: none; }
.btn.danger:hover { border-color: var(--danger); }

.search-input {
  border: 1px solid var(--line); border-radius: 6px; padding: 7px 12px;
  background: var(--panel); color: var(--text); font-size: 13px; min-width: 160px; outline: none;
}
.search-input:focus { border-color: var(--primary); }

.clipboard-bar {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px; margin-bottom: 16px;
  border-radius: 8px; background: var(--primary-light); border: 1px dashed var(--primary); font-size: 14px;
}

.file-table { width: 100%; border-collapse: collapse; background: var(--panel); border-radius: var(--radius); overflow: hidden; box-shadow: var(--shadow); }
.file-table th, .file-table td { text-align: left; padding: 10px 12px; border-bottom: 1px solid var(--line); font-size: 14px; }
.file-table th { background: var(--panel-2); font-weight: 500; color: var(--muted); font-size: 13px; }
tr.checked td { background: var(--primary-light); }

.col-check { width: 36px; text-align: center; }
.col-check input { accent-color: var(--primary); width: 16px; height: 16px; cursor: pointer; }

.sortable { cursor: pointer; user-select: none; }
.sortable:hover { color: var(--primary); }
.sortable em { font-style: normal; font-size: 11px; opacity: 0.6; }

.name-btn { display: flex; align-items: center; gap: 8px; border: 0; background: transparent; color: inherit; padding: 0; cursor: pointer; font-size: 14px; }
.icon { font-size: 16px; }

.row-actions { white-space: nowrap; }
.row-actions button { border: none; background: none; color: var(--muted); padding: 4px 6px; font-size: 13px; cursor: pointer; }
.row-actions button:hover { color: var(--primary); }
.row-actions .danger:hover { color: var(--danger); }

.batch-bar {
  display: flex; align-items: center; gap: 8px; padding: 12px 14px; margin-top: 16px;
  border-radius: 8px; background: var(--panel); border: 1px solid var(--line); box-shadow: var(--shadow);
}

.progress { position: relative; overflow: hidden; margin-bottom: 12px; padding: 10px 14px; border-radius: 8px; background: var(--panel-2); font-size: 14px; }
.progress i { position: absolute; left: 0; bottom: 0; height: 3px; background: var(--primary); }

.empty { min-height: 200px; display: grid; place-items: center; text-align: center; color: var(--muted); font-size: 15px; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.3); display: grid; place-items: center; z-index: 20; }
.modal-card { background: var(--panel); border-radius: var(--radius); padding: 28px; width: min(480px, 92vw); box-shadow: 0 8px 32px rgba(0,0,0,0.12); }
.modal-card.large { width: min(860px, 92vw); }
.modal-card h3 { margin: 0 0 16px; font-size: 18px; }
.modal-card input { width: 100%; padding: 10px 14px; border: 1px solid var(--line); border-radius: 8px; background: var(--panel-2); color: var(--text); font-size: 15px; margin-bottom: 16px; outline: none; }
.modal-card input:focus { border-color: var(--primary); }
.modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; }
.modal-card img, .modal-card iframe { width: 100%; min-height: 400px; border: 0; background: #000; border-radius: 8px; }

.admin-tabs { display: flex; gap: 0; border-bottom: 2px solid var(--line); margin-bottom: 20px; }
.admin-tabs button { padding: 10px 20px; border: none; background: none; color: var(--muted); font-size: 14px; font-weight: 500; border-bottom: 2px solid transparent; margin-bottom: -2px; cursor: pointer; }
.admin-tabs button.active { color: var(--primary); border-bottom-color: var(--primary); }

.admin-class-list { display: flex; flex-direction: column; gap: 12px; }
.admin-class-item { display: flex; justify-content: space-between; align-items: center; padding: 16px; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; box-shadow: var(--shadow); }
.admin-class-info { display: flex; align-items: center; gap: 16px; }
.admin-class-actions { display: flex; gap: 8px; }

.role-tag { padding: 2px 8px; border-radius: 10px; font-size: 12px; }
.role-tag.admin { background: var(--primary-light); color: var(--primary); }
.role-tag.user { background: var(--panel-2); color: var(--muted); }

.toast { position: fixed; right: 20px; bottom: 20px; padding: 12px 20px; border-radius: 8px; background: var(--panel); border: 1px solid var(--line); box-shadow: var(--shadow); font-size: 14px; z-index: 30; }

@media (max-width: 768px) {
  .section-header { flex-direction: column; align-items: flex-start; }
  .class-grid { grid-template-columns: 1fr; }
}
</style>
