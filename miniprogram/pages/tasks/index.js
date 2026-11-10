const { request } = require('../../utils/request')
const { ensureLogin } = require('../../utils/auth')

const QUEUE_TEXT = { RED: '临期', ORANGE: '有余额久未到', YELLOW: '有余额', BLUE: '未转化', NONE: '' }

Page({
  data: {
    loading: true,
    error: '',
    items: [],
    total: 0,
    doneCount: 0,
    queueText: QUEUE_TEXT
  },

  onShow() {
    this.load()
  },

  onPullDownRefresh() {
    this.load(() => wx.stopPullDownRefresh())
  },

  async load(done) {
    this.setData({ loading: true, error: '' })
    try {
      await ensureLogin()
      const d = await request({ url: '/api/followup/tasks?limit=100' })
      this.setData({
        loading: false,
        items: d.items || [],
        total: d.total || 0,
        doneCount: d.doneCount || 0
      })
    } catch (e) {
      this.setData({ loading: false, error: e.msg || '加载失败' })
    } finally {
      if (typeof done === 'function') done()
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: '/pages/member/index?id=' + e.currentTarget.dataset.id })
  }
})
