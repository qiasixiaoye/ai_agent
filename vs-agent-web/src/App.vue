<template>
  <!-- 缓存"产出型"页面，跳去审计/其它页再回来时不丢失已生成的结果；
       Observability 不缓存，保证每次带 requestId 进入都重新查询 -->
  <router-view v-slot="{ Component }">
    <keep-alive :include="['WorkflowStudio', 'AgentPlatform']">
      <component :is="Component" />
    </keep-alive>
  </router-view>
</template>

<style>
html, body {
  font-family: 'Helvetica Neue', Arial, sans-serif;
  margin: 0;
  padding: 0;
  background-color: #f0f2f5;
  color: #333;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
}

body {
  overflow-x: hidden;
}

#app {
  width:100%;
  min-height: 100vh;
}

* {
  box-sizing: border-box;
}
</style>
