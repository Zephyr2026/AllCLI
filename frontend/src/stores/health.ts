import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export const useHealthStore = defineStore('health', () => {
  const status = ref<'up' | 'down' | 'unknown'>('unknown')
  let timer: ReturnType<typeof setInterval> | null = null

  async function poll() {
    try {
      // /actuator/health 免 token、非信封，用裸 axios 即可（同样走 Vite proxy）
      const resp = await axios.get<{ status: string }>('/actuator/health', { timeout: 5000 })
      status.value = resp.data.status === 'UP' ? 'up' : 'down'
    } catch {
      status.value = 'down'
    }
  }

  function start() {
    if (timer) return
    void poll()
    timer = setInterval(poll, 30_000)
  }

  function stop() {
    if (timer) clearInterval(timer)
    timer = null
  }

  return { status, poll, start, stop }
})
