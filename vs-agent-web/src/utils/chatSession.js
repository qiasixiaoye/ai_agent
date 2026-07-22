const latestConversationId = (chats = {}) => Object.values(chats)
  .sort((left, right) => String(right.updatedAt || '').localeCompare(String(left.updatedAt || '')))[0]?.id

export const resolveChatSession = ({ chatStore, workbench, welcomeMessage }) => {
  const chats = chatStore.assistantAppChats || {}
  const currentId = workbench.currentConversationId
  const restoredId = chats[currentId] ? currentId : latestConversationId(chats)

  if (restoredId) {
    workbench.setConversation(restoredId)
    return { chatId: restoredId, created: false }
  }

  const chatId = chatStore.createConversation('normal')
  workbench.setConversation(chatId)
  chatStore.addMessage(chatId, {
    content: welcomeMessage,
    isUser: false,
    status: 'complete'
  })
  return { chatId, created: true }
}
