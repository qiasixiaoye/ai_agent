export const astroShootPlanSample = () => ({
  name: 'astro-shoot-plan',
  arguments: { latitude: 39.9042, longitude: 116.4074, date: '2026-08-15' }
})

export const selectAutoSkill = (decision) =>
  decision?.matched && decision.selectedSkillNames?.length ? decision.selectedSkillNames[0] : null

export const resolveAutoSkill = (decision, message) => {
  const name = selectAutoSkill(decision)
  if (name !== 'astro-shoot-plan') return null
  const lat = message.match(/(?:纬度|latitude)\s*[:：=]?\s*(-?\d+(?:\.\d+)?)/i)?.[1]
  const lon = message.match(/(?:经度|longitude)\s*[:：=]?\s*(-?\d+(?:\.\d+)?)/i)?.[1]
  const date = message.match(/\b(20\d{2}-\d{2}-\d{2})\b/)?.[1]
  if (!lat || !lon || !date) return null
  return { name, arguments: { latitude: Number(lat), longitude: Number(lon), date } }
}
