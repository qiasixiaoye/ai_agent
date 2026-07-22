import {
  buildWorkbenchSnapshot,
  readWorkbenchSnapshot,
  writeWorkbenchSnapshot
} from './workbenchPersistence.js'

export const shouldPersistWorkbenchChats = (chats = {}) =>
  Object.values(chats || {}).some((chat) => chat?.id)

export const applyWorkbenchSnapshotToStores = (snapshot, { chatStore, workbench } = {}) => {
  if (!snapshot || !chatStore || !workbench) return false
  chatStore.hydrateAssistantAppChats(snapshot.conversations || [])
  workbench.restorePersistedState(snapshot)
  return true
}

export const restoreWorkbenchSession = ({ chatStore, workbench, storage } = {}) => {
  const snapshot = readWorkbenchSnapshot(storage)
  return applyWorkbenchSnapshotToStores(snapshot, { chatStore, workbench })
}

export const persistWorkbenchSession = ({ workbench, chats, storage } = {}) => {
  if (!shouldPersistWorkbenchChats(chats)) return false
  writeWorkbenchSnapshot(buildWorkbenchSnapshot({
    workbench: workbench?.$state || workbench || {},
    chats
  }), storage)
  return true
}

