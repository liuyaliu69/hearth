const { BASE_URL } = require('../config/env')

/**
 * 统一请求封装。
 *
 * - 自动拼 BASE_URL
 * - 统一把后端的 { code, msg, data } 拆开：code=0 直接给 data，非 0 抛错
 * - 不在这里弹 toast（页面自己决定怎么提示，避免重复弹）
 */
function request({ url, method = 'GET', data, header = {}, timeout = 10000 }) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + url,
      method,
      data,
      timeout,
      header: {
        'content-type': 'application/json',
        ...header
      },
      success(res) {
        const body = res.data
        const ok = res.statusCode >= 200 && res.statusCode < 300 && body && body.code === 0
        if (ok) {
          resolve(body.data)
          return
        }
        const err = {
          statusCode: res.statusCode,
          code: body && body.code,
          msg: (body && body.msg) || `请求失败(${res.statusCode})`
        }
        console.warn('[hearth] request failed', url, err)
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

module.exports = { request }
