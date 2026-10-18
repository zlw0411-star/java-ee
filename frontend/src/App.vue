<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const token = ref(localStorage.getItem('token') || '')
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirmPassword: '' })
const showRegister = ref(false)
const dishes = ref([]), tables = ref([]), orders = ref([]), users = ref([]), report = ref(null), totalReport = ref(null), hotIds = ref([])
const reportRange = ref([])
const rangeReport = ref(null)
const dailyReport = ref([])
const categoryList = ref([])
const vipList = ref([])
const adminDishes = ref([])
const tableDialog = reactive({ visible: false, form: { id: null, tableNo: '', tableCode: '', seats: 4 } })
const dishDialog = reactive({ visible: false, form: { id: null, code: '', name: '', categoryId: null, price: 0, enabled: true } })
const vipDialog = reactive({ visible: false, form: { id: null, name: '', discount: 1 } })
const adminTab = ref('table')
const cart = reactive({})
const showCart = ref(false)
const tableCode = ref('')
const selectedTable = ref(null)
const waiterVip = ref(false)
const waiterVipLevel = ref(0.95)
const addingToOrder = ref(null)
const activeCategory = ref('全部')
const loggedIn = computed(() => !!token.value)
const headers = () => ({ Authorization: `Bearer ${token.value}` })
const roleText = computed(() => user.value?.role === 'ADMIN' ? '管理员' : user.value?.role === 'WAITER' ? '服务员' : '顾客')
const categories = computed(() => {
  const names = []
  const seen = new Set()
  for (const d of dishes.value) {
    const name = d.category?.name || '其他'
    if (!seen.has(name)) { seen.add(name); names.push(name) }
  }
  return names
})
const filteredDishes = computed(() => {
  const list = activeCategory.value === '全部' ? dishes.value : dishes.value.filter(d => (d.category?.name || '其他') === activeCategory.value)
  const hot = x => hotIds.value.includes(x.id) ? 1 : 0
  return [...list].sort((a, b) => hot(b) - hot(a))
})
const cartCount = computed(() => Object.values(cart).reduce((s, n) => s + (n || 0), 0))
const cartTotal = computed(() => dishes.value.reduce((s, d) => s + qty(d) * Number(d.price || 0), 0))
const cartItems = computed(() => dishes.value.filter(d => qty(d) > 0).map(d => ({ ...d, qty: qty(d), subtotal: qty(d) * Number(d.price || 0) })))
const hasDraftOrder = computed(() => orders.value.some(o => o.status === 'DRAFT'))
const vipRate = computed(() => {
  if (user.value?.role === 'CUSTOMER') {
    return (user.value.vipDiscount && Number(user.value.vipDiscount) < 1) ? Number(user.value.vipDiscount) : 1
  }
  if (user.value?.role === 'WAITER') {
    return waiterVip.value ? Number(waiterVipLevel.value) : 1
  }
  return 1
})
const vipLabel = computed(() => vipRate.value < 1 ? `VIP ${(vipRate.value * 10).toFixed(1).replace(/\.0$/, '')}折` : '')
const cartPayable = computed(() => cartTotal.value * vipRate.value)
const rangeOrders = computed(() => {
  if (!reportRange.value || reportRange.value.length !== 2) return []
  const from = new Date(reportRange.value[0] + 'T00:00:00').getTime()
  const to = new Date(reportRange.value[1] + 'T23:59:59').getTime()
  return orders.value.filter(o => {
    const t = new Date(o.createdAt).getTime()
    return o.status === 'SETTLED' && t >= from && t <= to
  })
})
const todayRevenue = computed(() => {
  if (!report.value) return '0.00'
  const value = Array.isArray(report.value) ? report.value[1] : report.value.revenue
  return Number(value || 0).toFixed(2)
})
const totalRevenue = computed(() => {
  if (!totalReport.value) return '0.00'
  const value = Array.isArray(totalReport.value) ? totalReport.value[1] : totalReport.value.revenue
  return Number(value || 0).toFixed(2)
})

function statusText(status) {
  return { DRAFT: '未提交', SUBMITTED: '已点好', SETTLED: '已结账', CANCELLED: '已取消' }[status] || status
}
function statusType(status) {
  return { DRAFT: 'info', SUBMITTED: 'warning', SETTLED: 'success', CANCELLED: 'danger' }[status] || 'info'
}

async function login() {
  try {
    const { data } = await axios.post('/api/user/auth/login', loginForm)
    token.value = data.token
    user.value = data.user
    localStorage.setItem('token', token.value)
    localStorage.setItem('user', JSON.stringify(user.value))
    await loadData()
    ElMessage.success('欢迎回来')
  } catch (e) { ElMessage.error(e.response?.data?.message || '登录失败') }
}
async function register() {
  if (!registerForm.username || !registerForm.password || !registerForm.confirmPassword) return ElMessage.warning('请填写完整信息')
  if (registerForm.password.length < 8) return ElMessage.warning('密码至少8位')
  if (registerForm.password !== registerForm.confirmPassword) return ElMessage.warning('两次密码不一致')
  try {
    await axios.post('/api/user/auth/register', {
      username: registerForm.username,
      password: registerForm.password,
      displayName: registerForm.username
    })
    ElMessage.success('注册成功，请登录')
    showRegister.value = false
    loginForm.username = registerForm.username
    registerForm.username = ''
    registerForm.password = ''
    registerForm.confirmPassword = ''
  } catch (e) { ElMessage.error(e.response?.data?.message || '注册失败') }
}
function logout() { token.value = ''; user.value = null; localStorage.clear() }
async function loadData() {
  const config = { headers: headers() }
  const [d, t, v] = await Promise.all([axios.get('/api/order/dishes', config), axios.get('/api/order/tables', config), axios.get('/api/user/vip-levels', config)])
  dishes.value = d.data
  tables.value = t.data
  vipList.value = v.data
  if (user.value?.role === 'CUSTOMER') {
    const r = await axios.get(`/api/order/orders/my?customerId=${user.value.id}`, config)
    orders.value = r.data
  } else {
    const r = await axios.get('/api/order/orders', config)
    orders.value = r.data
  }
  if (user.value?.role === 'ADMIN') {
    const r = await axios.get('/api/user/users', config)
    users.value = r.data
    await loadReport()
    await loadAdminData()
  }
  await loadHot()
}
function localToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
async function loadHot() {
  try {
    const { data } = await axios.get(`/api/order/reports/hot-dishes?from=2000-01-01&to=${localToday()}`, { headers: headers() })
    hotIds.value = data.slice(0, 3).map(r => r[0])
  } catch (e) { hotIds.value = [] }
}
async function loadReport() {
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  const [todayRes, allRes] = await Promise.all([
    axios.get(`/api/order/reports/revenue?from=${today}&to=${today}`, { headers: headers() }),
    axios.get(`/api/order/reports/revenue?from=2000-01-01&to=${today}`, { headers: headers() })
  ])
  report.value = todayRes.data[0] || [0, 0]
  totalReport.value = allRes.data[0] || [0, 0]
}function qty(dish) { return cart[dish.id] || 0 }
function add(dish) { cart[dish.id] = (cart[dish.id] || 0) + 1 }
function remove(dish) { if (cart[dish.id]) cart[dish.id]-- }
function clearCart() { Object.keys(cart).forEach(k => delete cart[k]) }
function selectTable(table) {
  if (table.status === 'OCCUPIED') return ElMessage.warning('这张桌子正在用餐')
  selectedTable.value = table
  clearCart()
}
function selectOrderToAdd(order) {
  addingToOrder.value = order
  clearCart()
  ElMessage.info(`正在为 ${order.orderNo} 加菜`)
}
function cancelAddDish() {
  addingToOrder.value = null
  clearCart()
}
async function submitItems(orderId) {
  for (const d of dishes.value) {
    if (qty(d) > 0) await axios.post(`/api/order/orders/${orderId}/items`, { dishId: d.id, quantity: qty(d) }, { headers: headers() })
  }
}
function findActiveOrderByTable(table) {
  if (!table) return null
  return orders.value.find(o => (o.status === 'DRAFT' || o.status === 'SUBMITTED') && o.table && o.table.tableNo === table.tableNo)
}
async function createOrder() {
  const table = tables.value.find(t => t.tableCode === tableCode.value || t.tableNo === tableCode.value)
  if (!table) return ElMessage.warning('请输入正确桌号')
  if (cartCount.value === 0) return ElMessage.warning('请先选择菜品')
  const existing = findActiveOrderByTable(table)
  if (existing) {
    await submitItems(existing.id)
    clearCart()
    showCart.value = false
    ElMessage.success(`已加入订单 ${existing.orderNo}（未提交）`)
    await loadData()
    return
  }
  try {
    const { data } = await axios.post('/api/order/orders', { tableId: table.id, mode: 'CUSTOMER', creatorId: user.value.id, customerId: user.value.id, tableCode: table.tableCode, discountRate: 1 }, { headers: headers() })
    await submitItems(data.id)
    clearCart()
    showCart.value = false
    ElMessage.success('已加入订单，确认无误后点【提交订单】')
    await loadData()
  } catch (e) {
    if (e.response?.status === 409) {
      await loadData()
      ElMessage.warning(e.response?.data?.message || '本桌订单暂时无法加入，请联系服务员')
    } else {
      ElMessage.error(e.response?.data?.message || '创建订单失败')
    }
  }
}
async function createWaiterOrder() {
  if (!selectedTable.value) return ElMessage.warning('请先选择餐桌')
  if (cartCount.value === 0) return ElMessage.warning('请先选择菜品')
  const existing = findActiveOrderByTable(selectedTable.value)
  if (existing) {
    await submitItems(existing.id)
    clearCart()
    showCart.value = false
    selectedTable.value = null
    ElMessage.success(`已加入订单 ${existing.orderNo}（未提交）`)
    await loadData()
    return
  }
  try {
    const { data } = await axios.post('/api/order/orders', { tableId: selectedTable.value.id, mode: 'WAITER', creatorId: user.value.id, customerId: null, tableCode: selectedTable.value.tableCode, discountRate: waiterVip.value ? waiterVipLevel.value : 1 }, { headers: headers() })
    await submitItems(data.id)
    clearCart()
    showCart.value = false
    selectedTable.value = null
    ElMessage.success('已加入订单（未提交），可在订单列表提交')
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '创建订单失败') }
}
async function addDishesToOrder() {
  if (!addingToOrder.value) return
  if (cartCount.value === 0) return ElMessage.warning('请先选择菜品')
  try {
    await submitItems(addingToOrder.value.id)
    clearCart()
    addingToOrder.value = null
    ElMessage.success('加菜成功')
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '追加菜品失败') }
}
async function settle(id) {
  try {
    await axios.post(`/api/order/orders/${id}/settle`, {}, { headers: headers() })
    ElMessage.success('已结账')
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '无权结算') }
}
async function submitOrder(id) {
  try {
    await axios.post(`/api/order/orders/${id}/submit`, {}, { headers: headers() })
    ElMessage.success('已提交，厨房可以出菜了')
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '提交失败') }
}
async function cancelOrder(id) {
  try {
    await axios.post(`/api/order/orders/${id}/cancel`, {}, { headers: headers() })
    ElMessage.success('已取消订单')
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '无权取消') }
}
async function queryReport() {
  if (!reportRange.value || reportRange.value.length !== 2) return ElMessage.warning('请先选择时间段')
  try {
    const [sum, daily] = await Promise.all([
      axios.get(`/api/order/reports/revenue?from=${reportRange.value[0]}&to=${reportRange.value[1]}`, { headers: headers() }),
      axios.get(`/api/order/reports/revenue/daily?from=${reportRange.value[0]}&to=${reportRange.value[1]}`, { headers: headers() })
    ])
    rangeReport.value = sum.data[0] || [0, 0]
    dailyReport.value = daily.data || []
  } catch (e) { ElMessage.error('查询失败') }
}
async function loadAdminData() {
  const config = { headers: headers() }
  const [c, v, d] = await Promise.all([axios.get('/api/order/categories', config), axios.get('/api/user/vip-levels', config), axios.get('/api/order/admin/dishes', config)])
  categoryList.value = c.data
  vipList.value = v.data
  adminDishes.value = d.data
}
function openTableDialog(row) {
  tableDialog.form = row
    ? { id: row.id, tableNo: row.tableNo, tableCode: row.tableCode, seats: row.seats }
    : { id: null, tableNo: '', tableCode: '', seats: 4 }
  tableDialog.visible = true
}
async function saveTable() {
  const f = tableDialog.form
  if (!f.tableNo || !f.tableCode || !f.seats) return ElMessage.warning('请填写完整')
  try {
    if (f.id) await axios.put(`/api/order/admin/tables/${f.id}`, f, { headers: headers() })
    else await axios.post('/api/order/admin/tables', f, { headers: headers() })
    ElMessage.success('已保存')
    tableDialog.visible = false
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') }
}
async function deleteTable(row) {
  try {
    await axios.delete(`/api/order/admin/tables/${row.id}`, { headers: headers() })
    ElMessage.success('已删除')
    await loadData()
  } catch (e) { ElMessage.error('删除失败，可能已被订单引用') }
}
function openDishDialog(row) {
  dishDialog.form = row
    ? { id: row.id, code: row.code, name: row.name, categoryId: row.category?.id, price: row.price, enabled: row.enabled }
    : { id: null, code: '', name: '', categoryId: categoryList.value[0]?.id || null, price: 0, enabled: true }
  dishDialog.visible = true
}
async function saveDish() {
  const f = dishDialog.form
  if (!f.code || !f.name || !f.categoryId || f.price === null || f.price === undefined || f.price === '') return ElMessage.warning('请填写完整')
  if (Number(f.price) < 0) return ElMessage.warning('价格不能为负')
  try {
    if (f.id) await axios.put(`/api/order/admin/dishes/${f.id}`, f, { headers: headers() })
    else await axios.post('/api/order/admin/dishes', f, { headers: headers() })
    ElMessage.success('已保存')
    dishDialog.visible = false
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') }
}
async function deleteDish(row) {
  try {
    await axios.delete(`/api/order/admin/dishes/${row.id}`, { headers: headers() })
    ElMessage.success('已删除')
    await loadData()
  } catch (e) { ElMessage.warning('该菜品已被历史订单引用，不能删除；请用「下架」让它从菜单隐藏') }
}
async function toggleDish(row) {
  try {
    await axios.put(`/api/order/admin/dishes/${row.id}`, { code: row.code, name: row.name, categoryId: row.category?.id, price: row.price, imageUrl: row.imageUrl, enabled: !row.enabled }, { headers: headers() })
    ElMessage.success(row.enabled ? '已下架' : '已上架')
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
}
function openVipDialog(row) {
  vipDialog.form = row ? { id: row.id, name: row.name, discount: row.discount } : { id: null, name: '', discount: 1 }
  vipDialog.visible = true
}
async function saveVip() {
  const f = vipDialog.form
  if (!f.name || f.discount === null || f.discount === undefined || f.discount === '') return ElMessage.warning('请填写完整')
  if (Number(f.discount) <= 0 || Number(f.discount) > 1) return ElMessage.warning('折扣需在 0~1 之间（如 0.9 表示9折）')
  try {
    if (f.id) await axios.put(`/api/user/vip-levels/${f.id}`, f, { headers: headers() })
    else await axios.post('/api/user/vip-levels', f, { headers: headers() })
    ElMessage.success('已保存')
    vipDialog.visible = false
    await loadData()
  } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') }
}
async function deleteVip(row) {
  try {
    await axios.delete(`/api/user/vip-levels/${row.id}`, { headers: headers() })
    ElMessage.success('已删除')
    await loadData()
  } catch (e) { ElMessage.warning('该等级可能已被用户使用，不能删除') }
}
async function assignVip(userRow, levelId) {
  try {
    await axios.put(`/api/user/users/${userRow.id}`, { vipLevelId: levelId }, { headers: headers() })
    ElMessage.success('已更新会员')
    await loadData()
  } catch (e) { ElMessage.error('更新失败') }
}
function confirmCart() {
  if (addingToOrder.value) return addDishesToOrder()
  if (user.value?.role === 'WAITER') return createWaiterOrder()
  return createOrder()
}
onMounted(() => { if (token.value) loadData() })
</script>

<template>
  <div class="app">
    <template v-if="!loggedIn">
      <div class="auth-bg">
        <div class="auth-side">
          <p class="brand-mark">SHAN FANG</p>
          <h1>膳坊</h1>
          <p>热菜出锅，一桌即开。<br>服务员代点，顾客也可自助下单。</p>
        </div>
        <div class="auth-panel">
          <div class="auth-card" v-if="!showRegister">
            <h2>欢迎回来</h2>
            <p class="lead">登录后按身份进入对应工作台</p>
            <el-form @submit.prevent="login" size="large">
              <el-form-item><el-input v-model="loginForm.username" placeholder="用户名" clearable /></el-form-item>
              <el-form-item><el-input v-model="loginForm.password" type="password" placeholder="密码" show-password @keyup.enter="login" /></el-form-item>
              <el-button type="primary" native-type="submit" class="full" size="large">登录</el-button>
            </el-form>
            <p class="switch">还没有账号？<button type="button" @click="showRegister=true">去注册</button></p>
            <div class="demo">
              <span>快速体验</span>
              <button type="button" @click="loginForm.username='admin';loginForm.password='Admin123'">管理员</button>
              <button type="button" @click="loginForm.username='waiter';loginForm.password='Waiter123'">服务员</button>
              <button type="button" @click="loginForm.username='customer';loginForm.password='Customer123'">顾客</button>
            </div>
          </div>
          <div class="auth-card" v-else>
            <h2>创建账号</h2>
            <p class="lead">注册后默认为顾客身份</p>
            <el-form @submit.prevent="register" size="large">
              <el-form-item><el-input v-model="registerForm.username" placeholder="用户名" clearable /></el-form-item>
              <el-form-item><el-input v-model="registerForm.password" type="password" placeholder="密码，至少8位" show-password /></el-form-item>
              <el-form-item><el-input v-model="registerForm.confirmPassword" type="password" placeholder="再次输入密码" show-password @keyup.enter="register" /></el-form-item>
              <el-button type="primary" native-type="submit" class="full" size="large">注册</el-button>
            </el-form>
            <p class="switch">已有账号？<button type="button" @click="showRegister=false">返回登录</button></p>
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <header class="topbar">
        <div class="brand">
          <strong>膳坊</strong>
          <span>{{ user.displayName }} · {{ roleText }}<template v-if="user.vipName"> · {{ user.vipName }}</template></span>
        </div>
        <el-button class="ghost" @click="logout">退出</el-button>
      </header>

      <main :class="{ padded: cartCount > 0 && user.role !== 'ADMIN' }">
        <section v-if="user.role==='ADMIN'" class="stack">
          <div class="metrics">
            <article><b>{{ orders.length }}</b><span>今日订单</span></article>
            <article><b>{{ tables.length }}</b><span>餐桌</span></article>
            <article><b>{{ dishes.length }}</b><span>在售菜品</span></article>
            <article><b>{{ users.length }}</b><span>用户</span></article>
          </div>
          <div class="panel revenue-panel">
            <div>
              <p>今日营业额</p>
              <strong>¥{{ todayRevenue }}</strong>
            </div>
            <div>
              <p>累计营业额</p>
              <strong>¥{{ totalRevenue }}</strong>
            </div>
          </div>
          <div class="panel">
            <div class="panel-head"><h3>营收报表</h3><small>选时间段，查看明细与汇总</small></div>
            <div class="report-filter">
              <el-date-picker v-model="reportRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" :clearable="true" />
              <el-button type="primary" @click="queryReport">查询</el-button>
            </div>
            <div class="report-summary" v-if="rangeReport">
              <span>订单数 <b>{{ rangeReport[0] }}</b></span>
              <span>营收合计 <b>¥{{ Number(rangeReport[1]).toFixed(2) }}</b></span>
            </div>
            <template v-if="dailyReport.length">
              <div class="sub-head">按天明细</div>
              <el-table :data="dailyReport" stripe size="small" style="margin-bottom:16px">
                <el-table-column label="日期" width="150"><template #default="s">{{ String(s.row[0]).slice(0,10) }}</template></el-table-column>
                <el-table-column label="订单数" width="110"><template #default="s">{{ s.row[1] }}</template></el-table-column>
                <el-table-column label="营收"><template #default="s">¥{{ Number(s.row[2]||0).toFixed(2) }}</template></el-table-column>
              </el-table>
            </template>
            <div class="sub-head" v-if="reportRange && reportRange.length===2">订单明细</div>
            <el-table v-if="reportRange && reportRange.length===2" :data="rangeOrders" stripe size="small">
              <el-table-column prop="orderNo" label="订单号" min-width="160"/>
              <el-table-column label="餐桌" width="90"><template #default="s">{{ s.row.table?.tableNo }}号</template></el-table-column>
              <el-table-column label="金额" width="120"><template #default="s">¥{{ Number(s.row.payAmount||0).toFixed(2) }}</template></el-table-column>
              <el-table-column label="日期" width="180"><template #default="s">{{ (s.row.createdAt||'').replace('T',' ').slice(0,16) }}</template></el-table-column>
            </el-table>
          </div>
          <div class="panel">
            <div class="panel-head"><h3>订单</h3></div>
            <el-table :data="orders" stripe>
              <el-table-column prop="orderNo" label="订单号" min-width="160"/>
              <el-table-column label="餐桌" width="90"><template #default="s">{{ s.row.table?.tableNo }}号</template></el-table-column>
              <el-table-column label="来源" width="90"><template #default="s">{{ s.row.orderMode==='WAITER' ? '服务员' : '顾客' }}</template></el-table-column>
              <el-table-column label="状态" width="110"><template #default="s"><el-tag :type="statusType(s.row.status)" effect="light">{{ statusText(s.row.status) }}</el-tag></template></el-table-column>
              <el-table-column label="应付" width="110"><template #default="s">¥{{ Number(s.row.payAmount||0).toFixed(2) }}</template></el-table-column>
              <el-table-column label="操作" width="220"><template #default="s"><el-button v-if="s.row.status==='DRAFT'" size="small" @click="submitOrder(s.row.id)">提交</el-button><el-button v-if="s.row.status==='SUBMITTED'" size="small" type="primary" @click="settle(s.row.id)">结账</el-button><el-button v-if="s.row.status==='DRAFT'||s.row.status==='SUBMITTED'" size="small" type="danger" @click="cancelOrder(s.row.id)">取消</el-button></template></el-table-column>
            </el-table>
          </div>
          <div class="panel">
            <div class="panel-head"><h3>用户</h3></div>
            <el-table :data="users" stripe>
              <el-table-column prop="username" label="账号"/>
              <el-table-column prop="displayName" label="名称"/>
              <el-table-column label="角色"><template #default="s">{{ s.row.role==='ADMIN'?'管理员':s.row.role==='WAITER'?'服务员':'顾客' }}</template></el-table-column>
              <el-table-column label="状态"><template #default="s"><el-tag :type="s.row.enabled?'success':'info'" effect="light">{{ s.row.enabled?'启用':'停用' }}</el-tag></template></el-table-column>
            </el-table>
          </div>
          <div class="panel">
            <div class="panel-head"><h3>基础资料维护</h3></div>
            <el-tabs v-model="adminTab">
              <el-tab-pane label="餐桌" name="table">
                <div class="mb"><el-button type="primary" size="small" @click="openTableDialog(null)">新增餐桌</el-button></div>
                <el-table :data="tables" stripe size="small">
                  <el-table-column prop="tableNo" label="桌号" width="90"/>
                  <el-table-column prop="tableCode" label="桌台码" min-width="120"/>
                  <el-table-column prop="seats" label="座位" width="80"/>
                  <el-table-column label="状态" width="100"><template #default="s"><el-tag :type="s.row.status==='AVAILABLE'?'success':'warning'" effect="light">{{ s.row.status==='AVAILABLE'?'空闲':'占用' }}</el-tag></template></el-table-column>
                  <el-table-column label="操作" width="160"><template #default="s"><el-button size="small" @click="openTableDialog(s.row)">编辑</el-button><el-button size="small" type="danger" @click="deleteTable(s.row)">删除</el-button></template></el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="菜品" name="dish">
                <div class="mb"><el-button type="primary" size="small" @click="openDishDialog(null)">新增菜品</el-button></div>
                <el-table :data="adminDishes" stripe size="small">
                  <el-table-column prop="code" label="编码" width="90"/>
                  <el-table-column prop="name" label="名称" min-width="130"/>
                  <el-table-column label="分类" width="90"><template #default="s">{{ s.row.category?.name }}</template></el-table-column>
                  <el-table-column label="价格" width="100"><template #default="s">¥{{ Number(s.row.price).toFixed(2) }}</template></el-table-column>
                  <el-table-column label="在售" width="90"><template #default="s"><el-switch :model-value="s.row.enabled" @change="toggleDish(s.row)" /></template></el-table-column>
                  <el-table-column label="操作" width="160"><template #default="s"><el-button size="small" @click="openDishDialog(s.row)">编辑</el-button><el-button size="small" type="danger" @click="deleteDish(s.row)">删除</el-button></template></el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="会员管理" name="vip">
                <div class="mb"><el-button type="primary" size="small" @click="openVipDialog(null)">新增等级</el-button></div>
                <el-table :data="vipList" stripe size="small" class="mb">
                  <el-table-column prop="name" label="等级" min-width="120"/>
                  <el-table-column label="折扣"><template #default="s">{{ Number(s.row.discount) < 1 ? (Number(s.row.discount)*10).toFixed(1).replace(/\.0$/,'') + ' 折' : '无折扣' }}</template></el-table-column>
                  <el-table-column label="操作" width="160"><template #default="s"><el-button size="small" @click="openVipDialog(s.row)">编辑</el-button><el-button size="small" type="danger" @click="deleteVip(s.row)">删除</el-button></template></el-table-column>
                </el-table>
                <el-divider>给顾客设置会员</el-divider>
                <el-table :data="users" stripe size="small">
                  <el-table-column prop="username" label="账号" width="120"/>
                  <el-table-column prop="displayName" label="名称" min-width="110"/>
                  <el-table-column label="角色" width="90"><template #default="s">{{ s.row.role==='ADMIN'?'管理员':s.row.role==='WAITER'?'服务员':'顾客' }}</template></el-table-column>
                  <el-table-column label="会员等级" width="200"><template #default="s">
                    <el-select :model-value="s.row.vipLevelId || 0" size="small" style="width:150px" @change="v => assignVip(s.row, v)">
                      <el-option label="无" :value="0" />
                      <el-option v-for="v in vipList" :key="v.id" :label="v.name" :value="v.id" />
                    </el-select>
                  </template></el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </div>
        </section>

        <section v-else-if="user.role==='WAITER'" class="stack">
          <div class="panel" v-if="!addingToOrder">
            <div class="panel-head"><h3>选桌</h3><small>点一张空桌开始点餐</small></div>
            <div class="table-grid">
              <button v-for="t in tables" :key="t.id" type="button" class="table-card" :class="{ selected: selectedTable?.id === t.id, occupied: t.status==='OCCUPIED' }" @click="selectTable(t)">
                <strong>{{ t.tableNo }}</strong>
                <span>{{ t.seats }}人 · {{ t.status==='AVAILABLE' ? '空闲' : '用餐中' }}</span>
              </button>
            </div>
          </div>
          <div class="panel accent" v-if="addingToOrder">
            <div class="panel-head"><h3>加菜</h3><el-button size="small" @click="cancelAddDish">取消</el-button></div>
            <p class="hint">正在为订单 {{ addingToOrder.orderNo }} 加菜</p>
          </div>
          <div class="panel" v-if="selectedTable || addingToOrder">
            <div class="panel-head">
              <h3>{{ addingToOrder ? '菜单' : selectedTable.tableNo + ' 号桌菜单' }}</h3>
              <div class="vip-toggle">
                <span>会员顾客</span>
                <el-switch v-model="waiterVip" />
                <el-select v-if="waiterVip" v-model="waiterVipLevel" size="small" style="width:170px">
                  <el-option v-for="v in vipList.filter(x => Number(x.discount) < 1)" :key="v.id" :label="`${v.name} ${(Number(v.discount)*10).toFixed(1).replace(/\.0$/,'')}折`" :value="Number(v.discount)" />
                </el-select>
              </div>
            </div>
            <div class="cats">
              <button type="button" :class="{ on: activeCategory==='全部' }" @click="activeCategory='全部'">全部</button>
              <button v-for="c in categories" :key="c" type="button" :class="{ on: activeCategory===c }" @click="activeCategory=c">{{ c }}</button>
            </div>
            <div class="dish-grid">
              <article v-for="d in filteredDishes" :key="d.id" class="dish">
                <div class="meta">
                  <div class="dish-top">
                    <b>{{ d.name }}<span v-if="hotIds.includes(d.id)" class="hot-tag">热销</span></b>
                    <span class="cat">{{ d.category?.name }}</span>
                  </div>
                  <div class="dish-bottom">
                    <em>¥{{ Number(d.price).toFixed(0) }}</em>
                    <div class="qty">
                      <button type="button" @click="remove(d)">−</button>
                      <strong>{{ qty(d) }}</strong>
                      <button type="button" class="plus" @click="add(d)">+</button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </div>
          <div class="panel">
            <div class="panel-head"><h3>堂食订单</h3></div>
            <el-table :data="orders" stripe>
              <el-table-column prop="orderNo" label="订单号" min-width="160"/>
              <el-table-column label="餐桌" width="90"><template #default="s">{{ s.row.table?.tableNo }}号</template></el-table-column>
              <el-table-column label="状态" width="110"><template #default="s"><el-tag :type="statusType(s.row.status)" effect="light">{{ statusText(s.row.status) }}</el-tag></template></el-table-column>
              <el-table-column label="应付" width="110"><template #default="s">¥{{ Number(s.row.payAmount||0).toFixed(2) }}</template></el-table-column>
              <el-table-column label="操作" width="300"><template #default="s">
                <el-button v-if="s.row.status==='DRAFT'||s.row.status==='SUBMITTED'" size="small" @click="selectOrderToAdd(s.row)">加菜</el-button>
                <el-button v-if="s.row.status==='DRAFT'" size="small" type="warning" @click="submitOrder(s.row.id)">提交</el-button>
                <el-button v-if="s.row.status==='SUBMITTED'" size="small" type="primary" @click="settle(s.row.id)">结账</el-button>
                <el-button v-if="s.row.status==='DRAFT'||s.row.status==='SUBMITTED'" size="small" type="danger" @click="cancelOrder(s.row.id)">取消</el-button>
              </template></el-table-column>
            </el-table>
          </div>
        </section>

        <section v-else class="stack">
          <div class="panel" v-if="!addingToOrder">
            <div class="panel-head"><h3>自助点餐</h3></div>
            <el-input v-model="tableCode" placeholder="输入桌号，例如 1" size="large">
              <template #prepend>桌号</template>
            </el-input>
            <p class="hint" v-if="!tableCode">先填桌号，菜单才会打开</p>
          </div>
          <div class="panel accent" v-if="addingToOrder">
            <div class="panel-head"><h3>加菜</h3><el-button size="small" @click="cancelAddDish">取消</el-button></div>
            <p class="hint">正在为订单 {{ addingToOrder.orderNo }} 加菜</p>
          </div>
          <div class="panel" v-if="tableCode || addingToOrder">
            <div class="panel-head"><h3>菜单</h3></div>
            <div class="cats">
              <button type="button" :class="{ on: activeCategory==='全部' }" @click="activeCategory='全部'">全部</button>
              <button v-for="c in categories" :key="c" type="button" :class="{ on: activeCategory===c }" @click="activeCategory=c">{{ c }}</button>
            </div>
            <div class="dish-grid">
              <article v-for="d in filteredDishes" :key="d.id" class="dish">
                <div class="meta">
                  <div class="dish-top">
                    <b>{{ d.name }}<span v-if="hotIds.includes(d.id)" class="hot-tag">热销</span></b>
                    <span class="cat">{{ d.category?.name }}</span>
                  </div>
                  <div class="dish-bottom">
                    <em>¥{{ Number(d.price).toFixed(0) }}</em>
                    <div class="qty">
                      <button type="button" @click="remove(d)">−</button>
                      <strong>{{ qty(d) }}</strong>
                      <button type="button" class="plus" @click="add(d)">+</button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </div>
          <div class="panel">
            <div class="panel-head"><h3>我的订单</h3></div>
            <el-alert v-if="hasDraftOrder" title="有订单尚未提交，选好菜后点【提交订单】通知上菜" type="warning" :closable="false" style="margin-bottom:12px" />
            <el-table :data="orders" stripe>
              <el-table-column prop="orderNo" label="订单号" min-width="160"/>
              <el-table-column label="餐桌" width="90"><template #default="s">{{ s.row.table?.tableNo }}号</template></el-table-column>
              <el-table-column label="状态" width="110"><template #default="s"><el-tag :type="statusType(s.row.status)" effect="light">{{ statusText(s.row.status) }}</el-tag></template></el-table-column>
              <el-table-column label="应付" width="110"><template #default="s">¥{{ Number(s.row.payAmount||0).toFixed(2) }}</template></el-table-column>
              <el-table-column label="操作" width="300"><template #default="s"><el-button v-if="s.row.status==='DRAFT'" size="small" type="warning" @click="submitOrder(s.row.id)">提交订单</el-button><el-button v-if="s.row.status==='DRAFT'||s.row.status==='SUBMITTED'" size="small" @click="selectOrderToAdd(s.row)">加菜</el-button><el-button v-if="s.row.status==='SUBMITTED'" size="small" type="primary" @click="settle(s.row.id)">结账</el-button></template></el-table-column>
            </el-table>
          </div>
        </section>
      </main>

      <el-dialog v-model="tableDialog.visible" title="餐桌信息" width="380px">
        <el-form label-width="72px">
          <el-form-item label="桌号"><el-input v-model="tableDialog.form.tableNo" placeholder="如 9" /></el-form-item>
          <el-form-item label="桌台码"><el-input v-model="tableDialog.form.tableCode" placeholder="如 9号桌" /></el-form-item>
          <el-form-item label="座位数"><el-input-number v-model="tableDialog.form.seats" :min="1" :max="20" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="tableDialog.visible=false">取消</el-button><el-button type="primary" @click="saveTable">保存</el-button></template>
      </el-dialog>
      <el-dialog v-model="dishDialog.visible" title="菜品信息" width="420px">
        <el-form label-width="72px">
          <el-form-item label="编码"><el-input v-model="dishDialog.form.code" placeholder="数字编号，如 215" /></el-form-item>
          <el-form-item label="名称"><el-input v-model="dishDialog.form.name" placeholder="菜名" /></el-form-item>
          <el-form-item label="分类">
            <el-select v-model="dishDialog.form.categoryId" placeholder="选择分类" style="width:100%">
              <el-option v-for="c in categoryList" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="价格"><el-input-number v-model="dishDialog.form.price" :min="0" :precision="2" :step="1" /></el-form-item>
          <el-form-item label="在售"><el-switch v-model="dishDialog.form.enabled" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="dishDialog.visible=false">取消</el-button><el-button type="primary" @click="saveDish">保存</el-button></template>
      </el-dialog>
      <el-dialog v-model="vipDialog.visible" title="会员等级" width="380px">
        <el-form label-width="72px">
          <el-form-item label="等级名"><el-input v-model="vipDialog.form.name" placeholder="如 钻石会员" /></el-form-item>
          <el-form-item label="折扣"><el-input-number v-model="vipDialog.form.discount" :min="0.1" :max="1" :step="0.05" :precision="2" /><span class="hint" style="margin-left:10px">1=无折扣，0.9=9折</span></el-form-item>
        </el-form>
        <template #footer><el-button @click="vipDialog.visible=false">取消</el-button><el-button type="primary" @click="saveVip">保存</el-button></template>
      </el-dialog>

      <transition name="cart-fade">
        <div class="cart-panel" v-if="loggedIn && user.role !== 'ADMIN' && showCart && cartCount > 0">
          <div class="cart-panel-head">
            <strong>已选菜品</strong>
            <button type="button" @click="clearCart">清空</button>
          </div>
          <ul>
            <li v-for="it in cartItems" :key="it.id">
              <div class="ci-name">{{ it.name }}<small>¥{{ Number(it.price).toFixed(0) }}</small></div>
              <div class="qty">
                <button type="button" @click="remove(it)">−</button>
                <strong>{{ it.qty }}</strong>
                <button type="button" class="plus" @click="add(it)">+</button>
              </div>
              <em>¥{{ it.subtotal.toFixed(2) }}</em>
            </li>
          </ul>
        </div>
      </transition>

      <div class="cart-bar" v-if="loggedIn && user.role !== 'ADMIN' && cartCount > 0">
        <div class="cart-summary" @click="showCart = !showCart">
          <span class="badge">{{ cartCount }}</span>
          <div>
            <small>已选 {{ cartCount }} 份 {{ showCart ? '▲' : '▼' }}</small>
            <strong v-if="vipRate < 1"><s class="old">¥{{ cartTotal.toFixed(2) }}</s> ¥{{ cartPayable.toFixed(2) }} <em class="vip-tag">{{ vipLabel }}</em></strong>
            <strong v-else>¥{{ cartTotal.toFixed(2) }}</strong>
          </div>
        </div>
        <el-button type="primary" size="large" @click="confirmCart">{{ addingToOrder ? '确认加菜' : '加入订单' }}</el-button>
      </div>
    </template>
  </div>
</template>

<style>
:root {
  --el-color-primary: #9c3b1e;
  --el-color-primary-light-3: #b25536;
  --el-color-primary-light-5: #c77a5d;
  --el-color-primary-light-7: #dc9f87;
  --el-color-primary-light-8: #e8baa8;
  --el-color-primary-light-9: #f4dcd1;
  --el-color-primary-dark-2: #7c2c14;
  --paper: #f5efe5;
  --ink: #2b1d15;
  --muted: #8c7a6b;
  --line: #e7dac9;
  --card: #fffdf9;
  --wood: #33221a;
  --wood-2: #4a3226;
  --gold: #c8963e;
  --red: #9c3b1e;
  --serif: "Noto Serif SC", "Songti SC", "STSong", "SimSun", serif;
}
* { box-sizing: border-box; }
html, body, #app { margin: 0; min-height: 100%; }
body {
  font-family: "PingFang SC", "Microsoft YaHei", "Source Han Sans SC", sans-serif;
  background:
    radial-gradient(1100px 520px at 50% -8%, #fdf4e5 0%, rgba(253,244,229,0) 60%),
    var(--paper);
  color: var(--ink);
}
button { font-family: inherit; }
h1, h2, h3 { font-family: var(--serif); }
</style>

<style scoped>
.app { min-height: 100vh; color: var(--ink); }
.full { width: 100%; }

/* ---------- 登录 ---------- */
.auth-bg {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background:
    linear-gradient(115deg, rgba(31,17,11,.82) 0%, rgba(120,52,28,.45) 55%, rgba(31,17,11,.55) 100%),
    url("https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=1600&q=80") center/cover;
}
.auth-side { color: #fff8ee; display: flex; flex-direction: column; justify-content: flex-end; padding: 8vh 8vw 10vh; }
.brand-mark { letter-spacing: .5em; font-size: 12px; opacity: .65; margin: 0 0 20px; }
.auth-side h1 { font-size: 72px; margin: 0; font-weight: 700; letter-spacing: .16em; text-shadow: 0 4px 30px rgba(0,0,0,.4); }
.auth-side h1::after { content: ""; display: block; width: 84px; height: 3px; margin-top: 22px; background: linear-gradient(90deg, var(--gold), transparent); }
.auth-side p { margin: 22px 0 0; line-height: 1.9; font-size: 16px; max-width: 380px; opacity: .9; }
.auth-panel { display: flex; align-items: center; justify-content: center; padding: 40px 24px; background: rgba(245,239,229,.95); }
.auth-card { width: 100%; max-width: 400px; }
.auth-card h2 { margin: 0; font-size: 30px; letter-spacing: .06em; }
.lead { color: var(--muted); margin: 10px 0 30px; }
.switch { text-align: center; color: var(--muted); margin-top: 18px; }
.switch button { border: 0; background: none; color: var(--red); cursor: pointer; font-size: 14px; border-bottom: 1px solid transparent; }
.switch button:hover { border-bottom-color: var(--red); }
.demo { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 30px; color: var(--muted); font-size: 13px; }
.demo button { border: 1px solid var(--line); background: #fff; color: var(--ink); border-radius: 999px; padding: 6px 14px; cursor: pointer; transition: .2s; }
.demo button:hover { border-color: var(--red); color: var(--red); background: #fdf1ea; }

/* ---------- 顶栏 ---------- */
.topbar {
  height: 76px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 6%;
  background: linear-gradient(180deg, var(--wood-2), var(--wood));
  color: #fff8ee;
  border-bottom: 3px solid var(--gold);
  box-shadow: 0 6px 24px rgba(51,34,26,.28);
}
.brand { display: flex; align-items: baseline; gap: 16px; }
.brand strong { font-family: var(--serif); font-size: 26px; letter-spacing: .28em; color: #fff8ee; }
.brand span { color: rgba(255,248,238,.62); font-size: 14px; }
.ghost { background: transparent !important; border-color: rgba(255,248,238,.3) !important; color: #fff8ee !important; }
.ghost:hover { background: rgba(200,150,62,.25) !important; border-color: var(--gold) !important; }
main { max-width: 1180px; margin: 0 auto; padding: 30px 20px 44px; }
main.padded { padding-bottom: 130px; }
.stack { display: grid; gap: 20px; }

/* ---------- 面板 ---------- */
.panel {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 22px;
  box-shadow: 0 10px 30px -18px rgba(51,34,26,.45);
}
.panel.accent { background: #fdf1e6; border-color: #eecfb4; }
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.panel-head h3 { margin: 0; font-size: 20px; letter-spacing: .05em; position: relative; padding-left: 14px; }
.panel-head h3::before { content: ""; position: absolute; left: 0; top: 4px; bottom: 4px; width: 4px; border-radius: 2px; background: linear-gradient(180deg, var(--red), var(--gold)); }
.panel-head small, .hint { color: var(--muted); }
.vip-toggle { display: flex; align-items: center; gap: 10px; font-size: 14px; color: var(--muted); }
.hint { margin: 12px 0 0; }

/* ---------- 统计 ---------- */
.metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.metrics article { background: linear-gradient(150deg, var(--wood-2), var(--wood)); color: #fff8ee; border-radius: 16px; padding: 24px; position: relative; overflow: hidden; }
.metrics article::after { content: ""; position: absolute; right: -30px; top: -30px; width: 96px; height: 96px; border-radius: 50%; background: radial-gradient(circle, rgba(200,150,62,.45), transparent 70%); }
.metrics b { display: block; font-size: 34px; font-family: var(--serif); }
.metrics span { opacity: .7; font-size: 13px; }
.revenue-panel { text-align: center; padding: 30px; display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.revenue-panel > div:first-child { border-right: 1px dashed var(--line); }
.revenue-panel p { margin: 0; color: var(--muted); letter-spacing: .28em; font-size: 14px; }
.revenue-panel strong { display: block; margin-top: 12px; font-size: 44px; font-family: var(--serif); color: var(--red); }
.report-filter { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 16px; }
.report-summary { display: flex; gap: 28px; padding: 14px 18px; background: #fbf3e9; border: 1px dashed var(--line); border-radius: 12px; margin-bottom: 16px; }
.report-summary span { color: var(--muted); font-size: 14px; }
.report-summary b { font-family: var(--serif); font-size: 22px; color: var(--red); margin-left: 6px; }
.sub-head { font-size: 14px; color: var(--muted); letter-spacing: .1em; margin: 0 0 8px; }

/* ---------- 选桌 ---------- */
.table-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 14px; }
.table-card {
  border: 1.5px solid var(--line);
  background: #fff;
  border-radius: 16px;
  padding: 24px 12px;
  cursor: pointer;
  display: grid;
  gap: 6px;
  transition: .22s transform, .22s box-shadow, .22s border-color;
}
.table-card strong { font-size: 30px; font-family: var(--serif); }
.table-card span { color: var(--muted); font-size: 13px; }
.table-card:hover { transform: translateY(-3px); border-color: var(--red); box-shadow: 0 12px 26px -14px rgba(156,59,30,.6); }
.table-card.selected { background: linear-gradient(150deg, #fdf1e6, #f8e0cf); border-color: var(--red); box-shadow: 0 12px 26px -12px rgba(156,59,30,.55); }
.table-card.occupied { background: #f3ece7; border-color: #e0cab9; color: #9c8574; cursor: not-allowed; }
.table-card.occupied:hover { transform: none; box-shadow: none; }

/* ---------- 分类 ---------- */
.cats { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px dashed var(--line); }
.cats button { border: 1px solid var(--line); background: #fff; border-radius: 999px; padding: 7px 16px; cursor: pointer; color: var(--ink); transition: .2s; }
.cats button:hover { border-color: var(--red); color: var(--red); }
.cats button.on { background: linear-gradient(150deg, var(--red), #7c2c14); color: #fff8ee; border-color: transparent; box-shadow: 0 8px 18px -10px rgba(156,59,30,.8); }

/* ---------- 菜品 ---------- */
.dish-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 14px; }
.dish { background: #fff; border: 1px solid var(--line); border-radius: 14px; overflow: hidden; transition: .22s transform, .22s box-shadow, .22s border-color; }
.dish:hover { transform: translateY(-3px); border-color: var(--gold); box-shadow: 0 16px 30px -20px rgba(51,34,26,.6); }
.meta { padding: 16px 18px; display: grid; gap: 14px; }
.dish-top { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.dish-top b { font-size: 17px; font-weight: 600; }
.hot-tag { font-style: normal; font-size: 11px; font-weight: 600; color: #fff8ee; background: linear-gradient(150deg, #e8692f, #c8340f); border-radius: 6px; padding: 1px 7px; margin-left: 8px; vertical-align: middle; }
.cat { color: var(--muted); font-size: 12px; border: 1px solid var(--line); border-radius: 999px; padding: 2px 10px; white-space: nowrap; }
.dish-bottom { display: flex; justify-content: space-between; align-items: center; }
.dish-bottom em { font-style: normal; color: var(--red); font-weight: 700; font-size: 22px; font-family: var(--serif); }
.qty { display: flex; align-items: center; gap: 12px; }
.qty strong { min-width: 22px; text-align: center; font-size: 16px; }
.qty button { width: 30px; height: 30px; border-radius: 50%; border: 1.5px solid var(--line); background: #fff; color: var(--ink); cursor: pointer; font-size: 16px; line-height: 1; transition: .18s; }
.qty button:hover { border-color: var(--red); color: var(--red); }
.qty .plus { background: linear-gradient(150deg, var(--red), #7c2c14); color: #fff8ee; border-color: transparent; box-shadow: 0 6px 14px -8px rgba(156,59,30,.9); }
.qty .plus:hover { color: #fff8ee; filter: brightness(1.08); }

/* ---------- 购物车 ---------- */
.cart-bar {
  position: fixed; left: 50%; bottom: 22px; transform: translateX(-50%);
  width: min(720px, calc(100% - 32px));
  background: linear-gradient(150deg, var(--wood-2), var(--wood));
  color: #fff8ee;
  border: 1px solid rgba(200,150,62,.55);
  border-radius: 20px; padding: 12px 12px 12px 20px;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 20px 44px -18px rgba(51,34,26,.85);
  z-index: 20;
}
.cart-bar > div:first-child { display: flex; align-items: center; gap: 14px; }
.cart-summary { cursor: pointer; user-select: none; }
.cart-bar small { display: block; opacity: .68; letter-spacing: .08em; }
.cart-bar strong { font-size: 24px; font-family: var(--serif); }
.cart-bar .old { color: rgba(255,248,238,.5); font-size: 15px; margin-right: 4px; font-family: inherit; }
.vip-tag { font-style: normal; font-size: 12px; background: linear-gradient(150deg, var(--gold), #a9782a); color: #33221a; border-radius: 6px; padding: 2px 6px; margin-left: 6px; vertical-align: middle; }
.badge { width: 38px; height: 38px; border-radius: 50%; background: linear-gradient(150deg, var(--gold), #a9782a); display: grid; place-items: center; font-weight: 700; color: #33221a; }

/* ---------- 购物车明细 ---------- */
.cart-panel {
  position: fixed; left: 50%; bottom: 108px; transform: translateX(-50%);
  width: min(720px, calc(100% - 32px));
  max-height: 46vh; overflow-y: auto;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 16px 18px;
  box-shadow: 0 24px 50px -20px rgba(51,34,26,.55);
  z-index: 19;
}
.cart-panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.cart-panel-head strong { font-family: var(--serif); font-size: 17px; letter-spacing: .05em; }
.cart-panel-head button { border: 0; background: none; color: var(--muted); cursor: pointer; font-size: 13px; }
.cart-panel-head button:hover { color: var(--red); }
.cart-panel ul { list-style: none; margin: 0; padding: 0; }
.cart-panel li { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 12px 0; border-bottom: 1px dashed var(--line); }
.cart-panel li:last-child { border-bottom: 0; }
.ci-name { flex: 1; font-size: 15px; }
.ci-name small { color: var(--muted); margin-left: 8px; }
.cart-panel li em { font-style: normal; font-family: var(--serif); color: var(--red); font-weight: 700; min-width: 72px; text-align: right; }
.cart-fade-enter-active, .cart-fade-leave-active { transition: opacity .2s, transform .2s; }
.cart-fade-enter-from, .cart-fade-leave-to { opacity: 0; transform: translate(-50%, 10px); }

/* ---------- 表格 ---------- */
:deep(.el-table) { --el-table-border-color: var(--line); --el-table-header-bg-color: #f7efe3; --el-table-row-hover-bg-color: #fdf4ea; border-radius: 12px; }
:deep(.el-table th.el-table__cell) { background: #f7efe3; color: #6b5443; font-weight: 600; }
:deep(.el-button) { border-radius: 10px; }
:deep(.el-input__wrapper) { border-radius: 10px; }

@media (max-width: 860px) {
  .auth-bg { grid-template-columns: 1fr; }
  .auth-side { display: none; }
  .metrics { grid-template-columns: 1fr 1fr; }
  main { padding: 20px 14px 40px; }
}
</style>
