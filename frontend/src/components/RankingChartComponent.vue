<template>
  <div class="chart-container">
    <div class="chart-header">
      <h3>{{ chartType === 'line' ? '오프셋 지연 곡선 (Offset-Latency Curve)' : '단일 랭킹 조회 속도 비교' }}</h3>
      <div class="scale-toggle" v-if="chartType === 'line'">
        <button 
          :class="['scale-btn', { active: yAxisType === 'linear' }]"
          @click="setYAxisType('linear')"
        >
          선형 축 (Linear)
        </button>
        <button 
          :class="['scale-btn', { active: yAxisType === 'logarithmic' }]"
          @click="setYAxisType('logarithmic')"
        >
          로그 축 (Logarithmic)
        </button>
      </div>
    </div>
    <div class="canvas-wrapper">
      <canvas ref="chartCanvas"></canvas>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

const props = defineProps({
  chartType: {
    type: String,
    default: 'bar' // 'bar' or 'line'
  },
  singleData: {
    type: Object,
    default: () => ({ mysqlTimeMs: 0, redisTimeMs: 0 })
  },
  curvePoints: {
    type: Array,
    default: () => []
  }
});

const chartCanvas = ref(null);
const yAxisType = ref('linear'); // 'linear' or 'logarithmic'
let chartInstance = null;

const setYAxisType = (type) => {
  yAxisType.value = type;
  renderChart();
};

const renderChart = () => {
  if (!chartCanvas.value) return;
  if (chartInstance) {
    chartInstance.destroy();
  }

  if (props.chartType === 'bar') {
    // Render single benchmark bar chart
    chartInstance = new Chart(chartCanvas.value, {
      type: 'bar',
      data: {
        labels: ['MySQL (Pagination)', 'Redis (ZSET ZREVRANGE)'],
        datasets: [{
          label: '지연 시간 (ms)',
          data: [props.singleData.mysqlTimeMs, props.singleData.redisTimeMs],
          backgroundColor: [
            'rgba(239, 68, 68, 0.75)', // Red
            'rgba(16, 185, 129, 0.75)' // Green
          ],
          borderColor: [
            'rgb(239, 68, 68)',
            'rgb(16, 185, 129)'
          ],
          borderWidth: 1.5,
          borderRadius: 8,
          barThickness: 45
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: false
          },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.95)',
            titleColor: '#f8fafc',
            bodyColor: '#e2e8f0',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1,
            padding: 12,
            callbacks: {
              label: (ctx) => `지연 시간: ${ctx.parsed.y.toFixed(4)} ms`
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#94a3b8',
              font: { family: 'Outfit, Inter, system-ui, sans-serif', weight: '500' }
            }
          },
          y: {
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#94a3b8',
              font: { family: 'Outfit, Inter, system-ui, sans-serif' },
              callback: (value) => `${value.toFixed(2)} ms`
            },
            title: {
              display: true,
              text: '응답 속도 (낮을수록 좋음)',
              color: '#94a3b8',
              font: { family: 'Outfit, Inter, system-ui, sans-serif', size: 11 }
            }
          }
        }
      }
    });
  } else if (props.chartType === 'line' && props.curvePoints.length > 0) {
    // Render curve line chart
    const sortedPoints = [...props.curvePoints].sort((a, b) => a.offset - b.offset);
    const labels = sortedPoints.map(p => p.offset.toLocaleString());
    const mysqlData = sortedPoints.map(p => p.mysqlTimeMs);
    const redisData = sortedPoints.map(p => p.redisTimeMs);

    chartInstance = new Chart(chartCanvas.value, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [
          {
            label: 'MySQL (Pagination)',
            data: mysqlData,
            borderColor: 'rgb(239, 68, 68)',
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            fill: true,
            tension: 0.15,
            borderWidth: 3,
            pointRadius: 4,
            pointHoverRadius: 6,
            pointBackgroundColor: 'rgb(239, 68, 68)'
          },
          {
            label: 'Redis (Sorted Set)',
            data: redisData,
            borderColor: 'rgb(16, 185, 129)',
            backgroundColor: 'rgba(16, 185, 129, 0.1)',
            fill: true,
            tension: 0.15,
            borderWidth: 3,
            pointRadius: 4,
            pointHoverRadius: 6,
            pointBackgroundColor: 'rgb(16, 185, 129)'
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'top',
            labels: {
              color: '#e2e8f0',
              font: { family: 'Outfit, Inter, system-ui, sans-serif', size: 12, weight: '500' },
              padding: 15
            }
          },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.95)',
            titleColor: '#f8fafc',
            bodyColor: '#e2e8f0',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1,
            padding: 12,
            callbacks: {
              title: (ctx) => `오프셋 (Offset): ${ctx[0].label}`,
              label: (ctx) => `${ctx.dataset.label}: ${ctx.parsed.y.toFixed(4)} ms`
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#94a3b8',
              font: { family: 'Outfit, Inter, system-ui, sans-serif' }
            },
            title: {
              display: true,
              text: '오프셋 크기 (Offset Size)',
              color: '#94a3b8',
              font: { family: 'Outfit, Inter, system-ui, sans-serif', size: 11 }
            }
          },
          y: {
            type: yAxisType.value,
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#94a3b8',
              font: { family: 'Outfit, Inter, system-ui, sans-serif' },
              callback: (value) => {
                if (yAxisType.value === 'logarithmic') {
                  if (value === 0.001 || value === 0.01 || value === 0.1 || value === 1 || value === 10 || value === 100 || value === 1000) {
                    return `${value} ms`;
                  }
                  return value.toString();
                }
                return `${value.toFixed(2)} ms`;
              }
            },
            title: {
              display: true,
              text: `평균 지연 시간 (${yAxisType.value === 'logarithmic' ? '로그 축' : '선형 축'} - ms)`,
              color: '#94a3b8',
              font: { family: 'Outfit, Inter, system-ui, sans-serif', size: 11 }
            }
          }
        }
      }
    });
  }
};

watch(() => props.singleData, () => {
  if (props.chartType === 'bar') renderChart();
}, { deep: true });

watch(() => props.curvePoints, () => {
  if (props.chartType === 'line') renderChart();
}, { deep: true });

watch(() => props.chartType, () => {
  renderChart();
});

onMounted(() => {
  renderChart();
});

onBeforeUnmount(() => {
  if (chartInstance) {
    chartInstance.destroy();
  }
});
</script>

<style scoped>
.chart-container {
  background: rgba(30, 41, 59, 0.4);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.chart-header h3 {
  margin: 0;
  font-size: 1.15rem;
  color: #f8fafc;
  font-weight: 600;
  letter-spacing: -0.025em;
}

.scale-toggle {
  display: flex;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 4px;
}

.scale-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.scale-btn:hover {
  color: #f8fafc;
}

.scale-btn.active {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

.canvas-wrapper {
  position: relative;
  flex-grow: 1;
  min-height: 320px;
  width: 100%;
}
</style>
