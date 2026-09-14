function getToken() {
  return localStorage.getItem('token') || ''
}

function authHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}`, Accept: 'application/json' } : { Accept: 'application/json' }
}

async function parse(res) {
  if (res.status === 401) {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    window.dispatchEvent(new CustomEvent('auth-expired'))
    throw new Error('登录已过期，请重新登录')
  }
  const data = await res.json().catch(() => ({}))
  if (!res.ok || data.ok === false) {
    throw new Error(data.message || `请求失败 (${res.status})`)
  }
  return data.data
}

export const api = {
  // Auth
  login: (username, password) =>
    fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ username, password }),
    }).then(parse),
  register: (username, password) =>
    fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ username, password }),
    }).then(parse),
  me: () => fetch('/api/auth/me', { headers: authHeaders() }).then(parse),
  health: () => fetch('/api/health').then((res) => res.json()),

  // Classes
  myClasses: () => fetch('/api/classes', { headers: authHeaders() }).then(parse),
  createClass: (name) =>
    fetch('/api/classes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ name }),
    }).then(parse),
  joinClass: (inviteCode) =>
    fetch('/api/classes/join', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ inviteCode }),
    }).then(parse),
  classMembers: (classId) =>
    fetch(`/api/classes/${classId}/members`, { headers: authHeaders() }).then(parse),
  removeMember: (classId, userId) =>
    fetch(`/api/classes/${classId}/members/${userId}`, { method: 'DELETE', headers: authHeaders() }).then(parse),
  deleteClass: (classId) =>
    fetch(`/api/classes/${classId}`, { method: 'DELETE', headers: authHeaders() }).then(parse),

  // Files (all require classId)
  list: (classId, path) =>
    fetch(`/api/files/list?classId=${classId}&path=${encodeURIComponent(path)}`, { headers: authHeaders() }).then(parse),
  search: (classId, q) =>
    fetch(`/api/files/search?classId=${classId}&q=${encodeURIComponent(q)}`, { headers: authHeaders() }).then(parse),
  stats: (classId) =>
    fetch(`/api/files/stats?classId=${classId}`, { headers: authHeaders() }).then(parse),
  recent: (classId) =>
    fetch(`/api/files/recent?classId=${classId}`, { headers: authHeaders() }).then(parse),
  mkdir: (classId, path) =>
    fetch(`/api/files/mkdir?classId=${classId}&path=${encodeURIComponent(path)}`, { method: 'POST', headers: authHeaders() }).then(parse),
  remove: (classId, path) =>
    fetch(`/api/files/delete?classId=${classId}&path=${encodeURIComponent(path)}`, { method: 'DELETE', headers: authHeaders() }).then(parse),
  rename: (classId, path, name) =>
    fetch(`/api/files/rename?classId=${classId}&path=${encodeURIComponent(path)}&name=${encodeURIComponent(name)}`, { method: 'POST', headers: authHeaders() }).then(parse),
  moveFile: (classId, from, to) =>
    fetch(`/api/files/move?classId=${classId}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, { method: 'POST', headers: authHeaders() }).then(parse),
  copyFile: (classId, from, to) =>
    fetch(`/api/files/copy?classId=${classId}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, { method: 'POST', headers: authHeaders() }).then(parse),
  batchDelete: (classId, paths) =>
    fetch(`/api/files/batch-delete?classId=${classId}&paths=${encodeURIComponent(paths.join(','))}`, { method: 'POST', headers: authHeaders() }).then(parse),
  upload: async (classId, path, file, onProgress, overwrite = false) => {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', `/api/files/upload?classId=${classId}&path=${encodeURIComponent(path)}&overwrite=${overwrite}`)
      const token = getToken()
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable && onProgress) {
          onProgress(Math.round((event.loaded / event.total) * 100))
        }
      }
      xhr.onload = () => {
        try {
          const data = JSON.parse(xhr.responseText || '{}')
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve(data)
          } else {
            reject(new Error(data.message || '上传失败'))
          }
        } catch (err) {
          reject(err)
        }
      }
      xhr.onerror = () => reject(new Error('网络错误'))
      const form = new FormData()
      form.append('file', file)
      xhr.send(form)
    })
  },

  // Share
  createShare: (path, expireHours = 24) =>
    fetch(`/api/share?path=${encodeURIComponent(path)}&expireHours=${expireHours}`, { method: 'POST', headers: authHeaders() }).then(parse),
  shares: () => fetch('/api/share', { headers: authHeaders() }).then(parse),

  // Admin
  adminUsers: () => fetch('/api/admin/users', { headers: authHeaders() }).then(parse),
  adminDeleteUser: (id) =>
    fetch(`/api/admin/users/${id}`, { method: 'DELETE', headers: authHeaders() }).then(parse),
  adminClasses: () => fetch('/api/admin/classes', { headers: authHeaders() }).then(parse),
}

export function downloadUrl(classId, path) {
  const token = getToken()
  return `/api/files/download?classId=${classId}&path=${encodeURIComponent(path)}${token ? `&token=${encodeURIComponent(token)}` : ''}`
}

export function previewUrl(classId, path) {
  const token = getToken()
  return `/api/files/preview?classId=${classId}&path=${encodeURIComponent(path)}${token ? `&token=${encodeURIComponent(token)}` : ''}`
}

export function shareUrl(token) {
  return `${window.location.origin}/s/${token}`
}

export function shareDownloadUrl(token) {
  return `/api/share/${token}`
}
