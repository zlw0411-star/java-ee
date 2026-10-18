import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import axios from 'axios'
import App from './App.vue'

axios.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 && localStorage.getItem('token')) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      alert('登录已过期，请重新登录')
      window.location.reload()
    }
    return Promise.reject(err)
  }
)

createApp(App).use(ElementPlus).mount('#app')
