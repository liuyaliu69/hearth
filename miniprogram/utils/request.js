const { BASE_URL } = require('../config/env')

const TOKEN_KEY = 'hearth_token'

/**
 * 统一请求封装。
 *
 * - 自动拼 BASE_URL
 * - 自动带 Authorization（除非 skipAuth）
 * - 统一把后端 { code, msg, data } 拆开：code=0 给 data，非 0 抛错
 * - 401 时**顺手清掉本地 token**，由页面决定重新登录
 *
 * 刻意不在这里调用 auth.js：那会形成 request ↔ auth 的循环依赖。
 * token 直接读 storage，认证编排放在 auth.js。
 */
function request({ url, method = 'GET', data, header = {}, timeout = 10000, skipAuth = false }) {
  return new Promise((resolve, reject) => {
    const finalHeader = { 'content-type': 'application/json', ...header }

    if (!skipAuth) {
      const token = wx.getStorageSync(TOKEN_KEY)
      if (token) {
        finalHeader['Authorization'] = 'Bearer ' + token
      }
    }

    wx.request({
      url: BASE_URL + url,
      method,
      data,
      timeout,
      header: finalHeader,
      success(res) {
        const body = res.data
        const ok = res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0
        if (ok) {
          resolve(body.data)
          return
        }

        if (res.statusCode === 401) {
          wx.removeStorageSync(TOKEN_KEY)
        }

        const err = {
          statusCode: res.statusCode,
          code: body && body.code,
          msg: (body && body.msg) || ('请求失败(' + res.statusCode + ')'),
          data: body && body.data
        }
        console.warn('[hearth] request failed', url, err.code, err.msg)
        reject(err)
      },
      fail(e) {
        const err = { statusCode: 0, code: -1, msg: e.errMsg || '网络错误' }
        console.warn('[hearth] request error', url, err)
        reject(err)
      }
    })
  })
}

module.exports = { request, TOKEN_KEY }
