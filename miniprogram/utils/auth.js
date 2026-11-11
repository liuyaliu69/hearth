const { request, TOKEN_KEY } = require('./request')
const { ENV, MOCK_CODE } = require('../config/env')

/**
 * 登录态管理。
 *
 * 流程：
 *   有 token → GET /auth/me 校验
 *       成功 → 直接返回用户
 *       401  → 清 token，走下面的静默登录
 *   无 token → 拿 code → POST /auth/login → 保存 token
 *
 * 拿 code 的两种方式：
 *   · 配了 WX_SECRET（真实模式）：wx.login 的真实 code → 微信换真实 openid
 *   · 没配（mock 模式）：dev 下用固定的 MOCK_CODE，否则每次都是新用户，永远登不进
 *
 * 未开通时后端返回 403 + data.openid，这里原样抛出，
 * 由页面跳到「未开通页」把 openid 展示给老师。
 */

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  wx.setStorageSync(TOKEN_KEY, token)
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY)
}

/** 包一层 Promise，wx.login 需要在回调里拿 code */
function wxLogin() {
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (res.code) {
          resolve(res.code)
        } else {
          reject({ statusCode: 0, code: -1, msg: 'wx.login 没有返回 code' })
        }
      },
      fail(e) {
        reject({ statusCode: 0, code: -1, msg: e.errMsg || 'wx.login 失败' })
      }
    })
  })
}

/** 取本次登录要用的 code */
async function resolveCode() {
  // 非生产环境 + 配了固定 code → 用它，保证 openid 稳定
  if (ENV !== 'prod' && MOCK_CODE) {
    return MOCK_CODE
  }
  return wxLogin()
}

/** 用 code 换 token */
async function login() {
  const code = await resolveCode()
  const data = await request({
    url: '/api/auth/login',
    method: 'POST',
    data: { code: code },
    skipAuth: true
  })
  setToken(data.token)
  return data
}

/**
 * 确保已登录。返回 { user, needMobile }
 * 未开通会抛 { statusCode: 403, data: { openid } }
 */
async function ensureLogin() {
  const token = getToken()

  if (token) {
    try {
      const me = await request({ url: '/api/auth/me' })
      return { user: me.user, needMobile: me.needMobile }
    } catch (e) {
      if (e.statusCode !== 401) {
        throw e
      }
      clearToken()
    }
  }

  const data = await login()
  return { user: data.user, needMobile: data.needMobile }
}

function logout() {
  clearToken()
  wx.reLaunch({ url: '/pages/login/index' })
}

module.exports = { ensureLogin, login, logout, getToken, setToken, clearToken }
