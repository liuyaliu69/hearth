const { ENV, BASE_URL } = require('./config/env')

App({
  globalData: {
    env: ENV,
    baseUrl: BASE_URL,
    token: '',
    user: null
  },

  onLaunch() {
    console.log('[hearth] env =', ENV, 'baseUrl =', BASE_URL)
  }
})
