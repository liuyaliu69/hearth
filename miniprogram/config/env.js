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

module.exports = { ENV, BASE_URL }
