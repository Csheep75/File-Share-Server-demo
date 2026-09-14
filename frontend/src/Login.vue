<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>文件共享服务器</h1>
        <p>局域网内的班级文件中转站</p>
      </div>

      <div class="tab-bar">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form @submit.prevent="submit" class="login-form">
        <div class="field">
          <label>用户名</label>
          <input v-model="username" placeholder="请输入用户名" autocomplete="username" required />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" required />
        </div>
        <div v-if="mode === 'register'" class="field">
          <label>确认密码</label>
          <input v-model="password2" type="password" placeholder="再次输入密码" autocomplete="new-password" />
        </div>
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? '处理中...' : (mode === 'login' ? '登录' : '注册') }}
        </button>
      </form>

      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { api } from './api'

const emit = defineEmits(['logged-in'])

const mode = ref('login')
const username = ref('')
const password = ref('')
const password2 = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请填写用户名和密码'
    return
  }
  if (mode.value === 'register') {
    if (password.value !== password2.value) {
      error.value = '两次密码不一致'
      return
    }
    if (password.value.length < 4) {
      error.value = '密码至少4个字符'
      return
    }
  }

  loading.value = true
  try {
    const data = mode.value === 'login'
      ? await api.login(username.value.trim(), password.value)
      : await api.register(username.value.trim(), password.value)
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify({ userId: data.userId, username: data.username, role: data.role }))
    emit('logged-in', data)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 20px;
}

.login-card {
  width: min(420px, 100%);
  background: var(--panel);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 40px 32px;
  border: 1px solid var(--line);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h1 {
  margin: 0;
  font-size: 24px;
  color: var(--text);
  font-weight: 600;
}

.login-header p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 14px;
}

.tab-bar {
  display: flex;
  gap: 0;
  margin-bottom: 28px;
  border-bottom: 2px solid var(--line);
}

.tab-bar button {
  flex: 1;
  padding: 10px;
  border: none;
  background: none;
  color: var(--muted);
  font-size: 15px;
  font-weight: 500;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.tab-bar button.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  font-size: 13px;
  color: var(--muted);
  font-weight: 500;
}

.field input,
.login-form input {
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel-2);
  color: var(--text);
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;
}

.field input:focus,
.login-form input:focus {
  border-color: var(--primary);
}

.submit-btn {
  padding: 12px;
  border: none;
  border-radius: 8px;
  background: var(--primary);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
  margin-top: 4px;
}

.submit-btn:hover:not(:disabled) {
  background: var(--primary-dark);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin: 16px 0 0;
  padding: 10px 14px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  color: var(--danger);
  font-size: 14px;
  text-align: center;
}
</style>
