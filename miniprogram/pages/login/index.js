const { ensureLogin, clearToken } = require('../../utils/auth')

Page({
  data: {
    openid: '',
    reason: '',
    retrying: false
  },

  onLoad(query) {
    this.setData({
      openid: decodeURIComponent(query.openid || ''),
      reason: decodeURIComponent(query.reason || '你的微信号还没开通')
    })
    // 到了未开通页说明当前 token 无效，清掉避免死循环
    clearToken()
  },

  onCopyOpenid() {
    if (!this.data.openid) {
      wx.showToast({ title: '没有 openid', icon: 'none' })
      return
    }
    wx.setClipboardData({
      data: this.data.openid,
      success: function () {
        wx.showToast({ title: '已复制，发给老板', icon: 'success' })
      }
    })
  },

  async onRetry() {
    this.setData({ retrying: true })
    try {
      const result = await ensureLogin()
      if (result && result.user) {
        wx.reLaunch({ url: '/pages/home/index' })
        return
      }
      wx.showToast({ title: '还没开通', icon: 'none' })
    } catch (e) {
      if (e.statusCode === 403) {
        this.setData({
          openid: (e.data && e.data.openid) || this.data.openid,
          reason: e.msg || this.data.reason
        })
        wx.showToast({ title: '还没开通', icon: 'none' })
      } else {
        wx.showToast({ title: e.msg || '重试失败', icon: 'none' })
      }
    } finally {
      this.setData({ retrying: false })
    }
  }
})
