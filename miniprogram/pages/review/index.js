const { request } = require('../../utils/request')
const { ensureLogin } = require('../../utils/auth')

Page({
  data: {
    loading: true,
    error: '',
    summary: null,
    reason: '',
    reasonLabel: '',
    items: [],
    busy: false
  },

  onShow() {
    this.loadSummary()
  },

  async loadSummary() {
    this.setData({ loading: true, error: '' })
    try {
      await ensureLogin()
      const s = await request({ url: '/api/followup/review/summary' })
      this.setData({ loading: false, summary: s })
    } catch (e) {
      this.setData({ loading: false, error: e.msg || '加载失败' })
    }
  },

  async loadItems(reason) {
    this.setData({ loading: true, reason: reason, items: [] })
    try {
      const d = await request({
        url: '/api/followup/review/items?reason=' + encodeURIComponent(reason) + '&page=1&size=50'
      })
      this.setData({ loading: false, items: d.items || [] })
    } catch (e) {
      this.setData({ loading: false, error: e.msg || '加载失败' })
    }
  },

  onPickGroup(e) {
    const reason = e.currentTarget.dataset.reason
    const label = e.currentTarget.dataset.label
    this.setData({ reasonLabel: label })
    this.loadItems(reason)
  },

  onBack() {
    this.setData({ reason: '', items: [] })
    this.loadSummary()
  },

  async onAct(e) {
    if (this.data.busy) return
    const id = e.currentTarget.dataset.id
    const action = e.currentTarget.dataset.action
    this.setData({ busy: true })
    try {
      await request({
        url: '/api/followup/review/act',
        method: 'POST',
        data: { snapshotId: id, action: action }
      })
      wx.showToast({ title: '已处理', icon: 'success' })
      this.loadItems(this.data.reason)
    } catch (err) {
      wx.showToast({ title: err.msg || '处理失败', icon: 'none' })
    } finally {
      this.setData({ busy: false })
    }
  },

  onBatch() {
    const that = this
    const reason = this.data.reason
    wx.showModal({
      title: '整批确认',
      content: '把「' + this.data.reasonLabel + '」这一组里还没核实的全部标记为可用？（最多 200 条）',
      success: async function (res) {
        if (!res.confirm) return
        try {
          const n = await request({
            url: '/api/followup/review/batch',
            method: 'POST',
            data: { reason: reason, action: 'CONFIRM', limit: 200 }
          })
          wx.showToast({ title: '处理了 ' + n + ' 条', icon: 'success' })
          that.loadItems(reason)
        } catch (err) {
          wx.showToast({ title: err.msg || '失败', icon: 'none' })
        }
      }
    })
  }
})
