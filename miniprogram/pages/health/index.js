const { request } = require('../../utils/request')
const { ENV, BASE_URL } = require('../../config/env')

Page({
  data: {
    loading: true,
    error: '',
    health: null,
    raw: '',
    env: ENV,
    baseUrl: BASE_URL
  },

  onLoad() {
    this.load()
  },

  onPullDownRefresh() {
    this.load(() => wx.stopPullDownRefresh())
  },

  load(done) {
    this.setData({ loading: true, error: '', health: null, raw: '' })
    request({ url: '/api/health' })
      .then((data) => {
        this.setData({
          loading: false,
          health: data,
          raw: JSON.stringify(data, null, 2)
        })
      })
      .catch((e) => {
        this.setData({
          loading: false,
          error: `${e.statusCode || ''} ${e.msg || '请求失败'}`.trim()
        })
      })
      .finally(() => {
        if (typeof done === 'function') done()
      })
  },

  onReload() {
    this.load()
  },

  onCopyRaw() {
    if (!this.data.raw) return
    wx.setClipboardData({
      data: this.data.raw,
      success: () => wx.showToast({ title: '已复制', icon: 'success' })
    })
  }
})
