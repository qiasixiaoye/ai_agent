export const createManusStreamState = () => ({
  answerText: '',
  trace: [],
  terminal: false,
  errorMessage: ''
})

export const applyManusStreamEvent = (state, event) => {
  if (!event || state.terminal) return state

  if (event.type === 'answer') {
    return { ...state, answerText: state.answerText + (event.text || '') }
  }

  if (['thinking', 'tool_call', 'tool_result'].includes(event.type)) {
    return { ...state, trace: [...state.trace, event] }
  }

  if (event.type === 'complete') {
    return { ...state, terminal: true }
  }

  if (event.type === 'error') {
    return { ...state, terminal: true, errorMessage: event.message || 'Manus execution failed' }
  }

  return state
}
