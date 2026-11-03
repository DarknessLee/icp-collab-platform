<template>
  <div ref="containerRef" class="wf-diagram" :style="{ height: `${height}px` }">
    <div v-if="loading" class="wf-diagram__mask" v-loading="true" element-loading-text="正在渲染流程图" />
    <el-empty v-else-if="errorMessage" :description="errorMessage" />
  </div>
</template>

<script setup lang="ts">
/**
 * bpmn-js 只读流程图（M2c）
 * - 定义页传 processKey，仅展示 BPMN；实例详情传 instanceId，自动请求 /diagram-highlight 高亮当前节点与已完成节点
 * - bpmn-js 通过动态 import 加载，避免进入首屏主包
 */
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { instanceApi, processApi } from '@/api/workflow'
import type { WfDiagramHighlight } from '@/types/workflow'

const props = withDefaults(
  defineProps<{
    processKey?: string
    instanceId?: string
    highlight?: WfDiagramHighlight | null
    height?: number
  }>(),
  { processKey: '', instanceId: '', highlight: null, height: 420 }
)

const containerRef = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const errorMessage = ref('')

interface ViewerLike {
  importXML: (xml: string) => Promise<{ warnings?: unknown[] }>
  get: (moduleName: string) => unknown
  destroy: () => void
}

let viewer: ViewerLike | null = null
let currentXml = ''

const BPMNDI = 'http://www.omg.org/spec/BPMN/20100524/DI'
const DC = 'http://www.omg.org/spec/DD/20100524/DC'
const DI = 'http://www.omg.org/spec/DD/20100524/DI'
const NODE_TAGS = new Set([
  'startEvent',
  'endEvent',
  'userTask',
  'serviceTask',
  'scriptTask',
  'manualTask',
  'intermediateCatchEvent',
  'exclusiveGateway',
  'parallelGateway',
  'inclusiveGateway'
])

/**
 * 后端 BPMN XML 未包含 BPMNDiagram（DI 布局）时，bpmn-js 会报 "no diagram to display"。
 * 这里按流程节点顺序生成一份水平布局的 DI，仅用于只读展示，不修改后端资源。
 */
function withAutoLayout(xml: string): string {
  if (!xml || xml.includes('BPMNDiagram')) return xml
  try {
    const doc = new DOMParser().parseFromString(xml, 'application/xml')
    if (doc.getElementsByTagName('parsererror').length > 0) return xml
    const processEl = doc.getElementsByTagName('process')[0]
    if (!processEl) return xml
    const children = Array.from(processEl.children)
    const nodes = children.filter((el) => NODE_TAGS.has(el.tagName))
    const flows = children.filter((el) => el.tagName === 'sequenceFlow')
    const rects = new Map<string, { x: number; y: number; w: number; h: number }>()
    nodes.forEach((el, index) => {
      const id = el.getAttribute('id') || `node_${index}`
      const isEvent = el.tagName.endsWith('Event') || el.tagName.endsWith('Gateway')
      const w = isEvent ? 36 : 120
      const h = isEvent ? 36 : 80
      rects.set(id, { x: 140 + index * 180, y: 160 - h / 2, w, h })
    })
    const diagram = doc.createElementNS(BPMNDI, 'bpmndi:BPMNDiagram')
    const plane = doc.createElementNS(BPMNDI, 'bpmndi:BPMNPlane')
    plane.setAttribute('bpmnElement', processEl.getAttribute('id') || '')
    diagram.appendChild(plane)
    for (const [id, rect] of rects) {
      const shape = doc.createElementNS(BPMNDI, 'bpmndi:BPMNShape')
      shape.setAttribute('bpmnElement', id)
      const bounds = doc.createElementNS(DC, 'dc:Bounds')
      bounds.setAttribute('x', String(rect.x))
      bounds.setAttribute('y', String(rect.y))
      bounds.setAttribute('width', String(rect.w))
      bounds.setAttribute('height', String(rect.h))
      shape.appendChild(bounds)
      plane.appendChild(shape)
    }
    for (const flow of flows) {
      const source = rects.get(flow.getAttribute('sourceRef') || '')
      const target = rects.get(flow.getAttribute('targetRef') || '')
      if (!source || !target) continue
      const edge = doc.createElementNS(BPMNDI, 'bpmndi:BPMNEdge')
      edge.setAttribute('bpmnElement', flow.getAttribute('id') || '')
      const points: Array<[number, number]> = [
        [source.x + source.w, source.y + source.h / 2],
        [target.x, target.y + target.h / 2]
      ]
      for (const [x, y] of points) {
        const waypoint = doc.createElementNS(DI, 'di:waypoint')
        waypoint.setAttribute('x', String(x))
        waypoint.setAttribute('y', String(y))
        edge.appendChild(waypoint)
      }
      plane.appendChild(edge)
    }
    doc.documentElement.appendChild(diagram)
    return new XMLSerializer().serializeToString(doc)
  } catch {
    return xml
  }
}

async function applyMarkers(highlight: WfDiagramHighlight | null): Promise<void> {
  if (!viewer || !highlight) return
  const canvas = viewer.get('canvas') as {
    addMarker: (id: string, marker: string) => void
    removeMarker: (id: string, marker: string) => void
    zoom: (level: string | number, center?: unknown) => void
  }
  try {
    canvas.removeMarker(highlight.activityId || '', 'wf-current')
  } catch {
    // 节点可能不存在于当前图
  }
  for (const id of highlight.finishedActivityIds || []) {
    try {
      canvas.addMarker(id, 'wf-finished')
    } catch {
      // 忽略不存在的节点
    }
  }
  if (highlight.activityId) {
    try {
      canvas.addMarker(highlight.activityId, 'wf-current')
    } catch {
      // 忽略不存在的节点
    }
  }
  try {
    canvas.zoom('fit-viewport')
  } catch {
    // 自动布局图可能缺少完整 viewbox，退化为原始比例
    try {
      canvas.zoom(1)
    } catch {
      // 忽略缩放失败
    }
  }
}

async function resolveHighlight(): Promise<WfDiagramHighlight | null> {
  if (props.highlight) return props.highlight
  if (props.instanceId) {
    try {
      return await instanceApi.highlight(props.instanceId)
    } catch {
      return null
    }
  }
  return null
}

async function resolveXml(): Promise<string> {
  if (currentXml) return currentXml
  if (props.processKey) {
    const xml = await processApi.diagram(props.processKey)
    currentXml = xml
    return xml
  }
  if (props.instanceId) {
    const detail = await instanceApi.detail(props.instanceId)
    const xml = await processApi.diagram(detail.processKey)
    currentXml = xml
    return xml
  }
  return ''
}

async function render(): Promise<void> {
  errorMessage.value = ''
  loading.value = true
  try {
    const xml = await resolveXml()
    if (!xml) {
      errorMessage.value = '暂无可渲染的流程图数据'
      return
    }
    const module = await import('bpmn-js/lib/Viewer')
    const ViewerCtor = ((module as unknown as { default?: new (opts: Record<string, unknown>) => ViewerLike }).default ??
      module) as unknown as new (opts: Record<string, unknown>) => ViewerLike
    await nextTick()
    if (!viewer) {
      viewer = new ViewerCtor({ container: containerRef.value })
    }
    await viewer.importXML(withAutoLayout(xml))
    await applyMarkers(await resolveHighlight())
  } catch (error) {
    errorMessage.value = `流程图加载失败：${(error as Error).message || '后端流程图接口不可用'}`
  } finally {
    loading.value = false
  }
}

onMounted(render)
watch(
  () => [props.processKey, props.instanceId],
  () => {
    currentXml = ''
    void render()
  }
)
watch(
  () => props.highlight,
  (value) => {
    void applyMarkers(value)
  }
)
onBeforeUnmount(() => {
  viewer?.destroy()
  viewer = null
})
</script>

<style scoped>
.wf-diagram {
  position: relative;
  width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fafbfc;
  overflow: hidden;
}

.wf-diagram__mask {
  position: absolute;
  inset: 0;
}

.wf-diagram :deep(svg) {
  max-width: 100%;
}

.wf-diagram :deep(.wf-current) :is(.djs-visual > :first-child) {
  stroke: #2f6fed !important;
  stroke-width: 2.5px !important;
  fill: #e8f1ff !important;
}

.wf-diagram :deep(.wf-finished) :is(.djs-visual > :first-child) {
  stroke: #18a058 !important;
  fill: #e6f7ec !important;
}
</style>
