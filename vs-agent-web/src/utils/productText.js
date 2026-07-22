export const OPTIONAL_CAPABILITY_FALLBACK =
  '暂未接入当前运行环境，可先使用已开启的对话、知识库和工作流能力。'

export const isOptionalCapabilityUnavailable = (error) => {
  const status = error?.response?.status
  const message = String(error?.message || '')
  return status === 404 || /status code 404|not found/i.test(message)
}

export const productErrorMessage = (error, capability = '该能力') => {
  if (isOptionalCapabilityUnavailable(error)) return `${capability}${OPTIONAL_CAPABILITY_FALLBACK}`
  return error?.response?.data?.message || error?.message || '请求失败，请稍后重试。'
}

export const statusLabel = (status) => ({
  healthy: '正常',
  up: '正常',
  down: '未连接',
  error: '未开启',
  unavailable: '未接入',
  unknown: '待配置',
  complete: '完成',
  failed: '失败',
  incomplete: '未完成'
}[String(status || '').toLowerCase()] || status || '待配置')

export const riskLabel = (risk) => ({
  safe: '可直接调用',
  confirm: '需要确认',
  blocked: '已被策略阻止',
  unknown: '待评估'
}[String(risk || '').toLowerCase()] || '待评估')

export const localizedDescription = (value, fallback = '该能力暂未提供中文说明。') => {
  const text = String(value || '').trim()
  if (!text) return fallback
  return /[\u4e00-\u9fff]/.test(text) ? text : fallback
}
