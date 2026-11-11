/**
 * 环境配置 —— 全项目唯一改地址的地方。
 *
 * dev    模拟器直连本机后端（无 nginx 前缀）
 * devip  真机调试，换成你的电脑局域网 IP（手机上 127.0.0.1 指手机自己）
 * prod   线上，走 nginx 的 /hearth/ 前缀
 *
 * 三种情况下后端收到的路径都是 /api/*，前缀由 nginx 剥掉 —— 这是整套配置唯一的约定。
 */

// ★ 切换环境只改这一行
const ENV = 'dev'

const LAN_IP = '192.168.1.8' // ← 真机调试时改成 ipconfig 里的 IPv4 地址

const BASE_URL = {
  dev: 'http://127.0.0.1:8080',
  devip: `http://${LAN_IP}:8080`,
  prod: 'https://www.huohuawd.cn/hearth'
}[ENV]

if (!BASE_URL) {
  throw new Error(`[hearth] 未知环境: ${ENV}`)
}

/**
 * ★ 开发期的「我是谁」
 *
 * 后端没配 WX_SECRET 时走 mock：openid = "mock_" + code。
 * 但开发者工具每次 wx.login 拿到的 code 都不一样，
 * 等于每次都是一个新微信号 —— 永远登不进已开通的账号。
 *
 * 所以 dev / devip 下改用固定 code，openid 就稳定了：
 *
 *   MOCK_CODE = 'owner1'  → openid = mock_owner1 → 老板
 *   MOCK_CODE = 'qixi1'   → openid = mock_qixi1  → 柒柒（老师视角）
 *
 * 换成哪个 code，就等于以哪个账号登录。prod 下这个值必须为空串。
 */
const MOCK_CODE = 'owner1'

module.exports = { ENV, BASE_URL, MOCK_CODE }
