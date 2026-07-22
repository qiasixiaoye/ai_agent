export const streamClosureStatus = (responseContent) => responseContent ? 'complete' : 'error'

export const parseJsonObject = (input, label = 'Arguments') => {
  let parsed
  try {
    parsed = JSON.parse(input)
  } catch {
    throw new Error(`${label} must be valid JSON.`)
  }

  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error(`${label} must be a JSON object.`)
  }

  return parsed
}
