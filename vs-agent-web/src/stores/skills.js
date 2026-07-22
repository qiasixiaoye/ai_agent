import { defineStore } from 'pinia'
import { listSkills, getSkill, executeSkill, previewSkillRoute, evaluateSkillRouting } from '../services/api'

const messageOf = (error) => error?.message || 'Request failed'

export const useSkillsStore = defineStore('skills', {
  state: () => ({
    catalog: [],
    selected: null,
    routePreview: null,
    evaluation: null,
    loading: false,
    executing: false,
    error: '',
    lastResult: null
  }),
  actions: {
    async loadSkills() {
      this.loading = true
      try {
        this.catalog = await listSkills()
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async selectSkill(name) {
      this.loading = true
      try {
        this.selected = await getSkill(name)
        this.error = ''
      } catch (error) {
        this.error = messageOf(error)
      } finally {
        this.loading = false
      }
    },
    async previewRoute(query, topK = 3, threshold = 0.24) {
      try {
        this.routePreview = await previewSkillRoute(query, topK, threshold)
        this.error = ''
        return this.routePreview
      } catch (error) {
        this.error = messageOf(error)
        throw error
      }
    },
    async evaluateRouting() {
      try {
        this.evaluation = await evaluateSkillRouting()
        this.error = ''
        return this.evaluation
      } catch (error) {
        this.error = messageOf(error)
        throw error
      }
    },
    async executeSelected(args) {
      if (!this.selected?.name) throw new Error('No Skill selected')
      this.executing = true
      try {
        this.lastResult = await executeSkill(this.selected.name, args)
        this.error = ''
        return this.lastResult
      } catch (error) {
        this.error = messageOf(error)
        throw error
      } finally {
        this.executing = false
      }
    }
  }
})
