const { request } = require('../../utils/request')
const { ensureLogin } = require('../../utils/auth')

const RESULTS = [
  { code: 1, text: '已续费' },
  { code: 2, text: '有意向' },
  { code: 3, text: '考虑中' },
  { code: 4, text: '没时间' },
  { code: 5, text: '不续' },
  { code: 6, text: '未接通' },
  { code: 7, text: '要顺延' },
  { code: 8, text: '已流失' },
  { code: 9, text: '其他' }
]

Page({
  data: {
    loading: true,
    error: '',
    detail: null,
    results: RESULTS,
    submitting: false
  },

  onLoad(query) {
    this.snapshotId = query.id
    this.load()
  },

  async load() {
    this.setData({ loading: true, error: '' })
    try {
      await ensureLogin()
      const d = await request({ url: '/api/followup/members/' + this.snapshotId })
      this.setData({ loading: false, detail: d })
    } catch (e) {
      this.setData({ loading: false, error: e.msg || '加载失败' })
    }
  },

  async onSubmit(e) {
    if (this.data.submitting) return
    const code = e.currentTarget.dataset.code
    const label = RESULTS.find(function (r) { return r.code === code }).text
    this.setData({ submitting: true })
    try {
      await request({
        url: '/api/followup/followups',
        method: 'POST',
        data: { memberId: this.data.detail.memberId, resultCode: code }
      })
      wx.showToast({ title: '已记录：' + label, icon: 'success' })
      setTimeout(function () { wx.navigateBack() }, 800)
    } catch (err) {
      wx.showToast({ title: err.msg || '提交失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  onFlag() {
    const that = this
    wx.showModal({
      title: '信息有误',
      editable: true,
      placeholderText: '哪里不对？例如手机号换了',
      success: async function (res) {
        if (!res.confirm) return
        const note = (res.content || '').trim()
        if (!note) {
          wx.showToast({ title: '请写明原因', icon: 'none' })
          return
        }
        try {
          await request({
            url: '/api/followup/members/' + that.data.detail.memberId + '/flag',
            method: 'POST',
            data: { note: note }
          })
          wx.showToast({ title: '已退回给老板', icon: 'success' })
          setTimeout(function () { wx.navigateBack() }, 800)
        } catch (err) {
          wx.showToast({ title: err.msg || '退回失败', icon: 'none' })
        }
      }
    })
  }
})
