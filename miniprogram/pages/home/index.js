const { request } = require('../../utils/request')
const { ensureLogin } = require('../../utils/auth')

const ROLE_NAMES = { owner: '老板', teacher: '老师', operator: '运营' }
const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

Page({
  data: {
    loading: true,
    error: '',
    user: null,
    roleText: '',
    isOwner: false,
    needMobile: false,
    stats: null,
    progress: 0,
    dateText: ''
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
      const result = await ensureLogin()
      const user = result.user || {}
      const roles = user.roles || []
      const isOwner = roles.indexOf('owner') >= 0

      this.setData({
        user: user,
        roleText: roles.map(function (r) { return ROLE_NAMES[r] || r }).join('、'),
        isOwner: isOwner,
        needMobile: result.needMobile,
        dateText: this.todayText()
      })

      // 统计失败不挡首页（比如老师刚开通还没数据）
      let stats = null
      try {
        stats = await request({ url: '/api/followup/stats' })
      } catch (e) {
        stats = null
      }

      const total = stats ? stats.total : 0
      const doneCount = stats ? stats.done : 0
      this.setData({
        loading: false,
        stats: stats,
        progress: total > 0 ? Math.round(doneCount * 100 / total) : 0
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
    } finally {
      if (typeof done === 'function') done()
    }
  },

  todayText() {
    const d = new Date()
    return (d.getMonth() + 1) + ' 月 ' + d.getDate() + ' 日 · ' + WEEK[d.getDay()]
  },

  onTasks() {
    wx.navigateTo({ url: '/pages/tasks/index' })
  },

  onReview() {
    wx.navigateTo({ url: '/pages/review/index' })
  },

  onMe() {
    wx.navigateTo({ url: '/pages/me/index' })
  }
})
