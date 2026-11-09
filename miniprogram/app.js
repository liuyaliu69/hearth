const { ENV, BASE_URL } = require('./config/env')

App({
  globalData: {
    env: ENV,
    baseUrl: BASE_URL,
    user: null,
    needMobile: false
  },

  onLaunch() {
    console.log('[hearth] env =', ENV, 'baseUrl =', BASE_URL)
    // 登录态由各页面在 onLoad 里通过 ensureLogin() 处理，
    // 不在这里跳转 —— onLaunch 阶段页面栈还没准备好。
  }
})
