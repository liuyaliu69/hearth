const { ensureLogin, logout } = require('../../utils/auth')

const ROLE_NAMES = { owner: '老板', teacher: '老师', operator: '运营' }

Page({
  data: {
    loading: true,
    error: '',
    user: null,
    roleText: '',
    needMobile: false,
    mobileText: ''
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

      getApp().globalData.user = user
      getApp().globalData.needMobile = result.needMobile

      this.setData({
        loading: false,
        user: user,
        roleText: roleText,
        needMobile: result.needMobile,
        mobileText: ''
      })
    } catch (e) {
      this.setData({ loading: false })

      // 未开通：带着 openid 跳未开通页，让老师能把它发给老板
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

  onReload() {
    this.load()
  },

  onGoHealth() {
    wx.navigateTo({ url: '/pages/health/index' })
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '退出后需要重新进入小程序才能登录',
      success: function (res) {
        if (res.confirm) {
          logout()
        }
      }
    })
  }
})
