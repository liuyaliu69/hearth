const { ensureLogin, logout } = require('../../utils/auth')
const { request } = require('../../utils/request')

const ROLE_NAMES = { owner: '老板', teacher: '老师', operator: '运营' }

Page({
  data: {
    loading: true,
    error: '',
    user: null,
    roleText: '',
    isOwner: false,
    needMobile: false,
    stats: null
  },

  onShow() {
    this.load()
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      const result = await ensureLogin()
      const user = result.user || {}
      const roles = user.roles || []
      const roleText = roles.map(function (r) { return ROLE_NAMES[r] || r }).join('、')

      this.setData({
        loading: false,
        user: user,
        roleText: roleText,
        isOwner: roles.indexOf('owner') >= 0,
        needMobile: result.needMobile
      })

      // 统计失败不该影响页面
      try {
        const stats = await request({ url: '/api/followup/stats' })
        this.setData({ stats: stats })
      } catch (e) {
        this.setData({ stats: null })
      }
    } catch (e) {
      this.setData({ loading: false })
      if (e.statusCode === 403) {
        const openid = (e.data && e.data.openid) || ''
        wx.redirectTo({
          url: '/pages/login/index?openid=' + encodeURIComponent(openid) +
            '&reason=' + encodeURIComponent(e.msg || '')
        })
        return
      }
      this.setData({ error: e.msg || '加载失败' })
    }
  },

  onTasks() {
    wx.navigateTo({ url: '/pages/tasks/index' })
  },

  onReview() {
    wx.navigateTo({ url: '/pages/review/index' })
  },

  onGoHealth() {
    wx.navigateTo({ url: '/pages/health/index' })
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新进入小程序才能登录',
      success: function (res) {
        if (res.confirm) logout()
      }
    })
  }
})
