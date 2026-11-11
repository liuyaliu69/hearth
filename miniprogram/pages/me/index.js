const { ensureLogin, logout } = require('../../utils/auth')

const ROLE_NAMES = { owner: '老板', teacher: '老师', operator: '运营' }

/**
 * 我的 = 账号与设置。
 * 统计数据在首页，这里不重复。
 */
Page({
  data: {
    loading: true,
    error: '',
    user: null,
    roleText: '',
    needMobile: false
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
      this.setData({
        loading: false,
        user: user,
        roleText: roles.map(function (r) { return ROLE_NAMES[r] || r }).join('、'),
        needMobile: result.needMobile
      })
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
