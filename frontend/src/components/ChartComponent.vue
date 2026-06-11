<template>
  <div class="chart-container">
    <div class="chart-header">
      <h3>성능 비교 그래프</h3>
      <div class="tab-group">
        <button 
          :class="['tab-btn', { active: metricType === 'opsPerSec' }]"
          @click="setMetricType('opsPerSec')"
        >
          초당 처리량 (Ops/sec)
        </button>
        <button 
          :class="['tab-btn', { active: metricType === 'avgTimeMs' }]"
          @click="setMetricType('avgTimeMs')"
        >
          평균 응답 속도 (ms)
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
  results: {
    type: Array,
    required: true
  }
});

const chartCanvas = ref(null);
const metricType = ref('opsPerSec'); // 'opsPerSec' or 'avgTimeMs'
let chartInstance = null;

const targetColors = {
  'MYSQL_JPA': { fill: 'rgba(239, 68, 68, 0.65)', border: 'rgb(239, 68, 68)' },
  'MYSQL_JDBC': { fill: 'rgba(245, 158, 11, 0.65)', border: 'rgb(245, 158, 11)' },
  'REDIS': { fill: 'rgba(16, 185, 129, 0.65)', border: 'rgb(16, 185, 129)' },
  'REDIS_PIPELINE': { fill: 'rgba(139, 92, 246, 0.65)', border: 'rgb(139, 92, 246)' }
};

const targetLabels = {
  'MYSQL_JPA': 'MySQL (JPA)',
  'MYSQL_JDBC': 'MySQL (JDBC)',
  'REDIS': 'Redis (Standard)',
  'REDIS_PIPELINE': 'Redis (Pipeline)'
};

const operationLabels = {
  'WRITE_SINGLE': '단건 쓰기',
  'WRITE_BATCH': '배치 쓰기',
  'READ_SINGLE': '단건 읽기',
  'READ_BATCH': '배치 읽기'
};

const setMetricType = (type) => {
  metricType.value = type;
  renderChart();
};

const renderChart = () => {
  if (!chartCanvas.value) return;
  if (chartInstance) {
    chartInstance.destroy();
  }

  // Get unique operations and targets from results
  const operations = [...new Set(props.results.map(r => r.operation))];
  const targets = [...new Set(props.results.map(r => r.target))];

  // Sort operations to keep consistent order
  const order = ['WRITE_SINGLE', 'WRITE_BATCH', 'READ_SINGLE', 'READ_BATCH'];
  operations.sort((a, b) => order.indexOf(a) - order.indexOf(b));

  // Build labels (X-axis)
  const labels = operations.map(op => operationLabels[op] || op);

  // Build datasets (one per target)
  const datasets = targets.map(target => {
    const data = operations.map(op => {
      const match = props.results.find(r => r.target === target && r.operation === op);
      if (!match) return 0;
      return metricType.value === 'opsPerSec' ? match.opsPerSec : match.avgTimeMs;
    });

    const colors = targetColors[target] || { fill: 'rgba(156, 163, 175, 0.65)', border: 'rgb(156, 163, 175)' };

    return {
      label: targetLabels[target] || target,
      data: data,
      backgroundColor: colors.fill,
      borderColor: colors.border,
      borderWidth: 1.5,
      borderRadius: 4,
      barPercentage: 0.7,
      categoryPercentage: 0.6
    };
  });

  const isThroughput = metricType.value === 'opsPerSec';

  chartInstance = new Chart(chartCanvas.value, {
    type: 'bar',
    data: {
      labels: labels,
      datasets: datasets
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'top',
          labels: {
            color: '#e2e8f0',
            font: {
              family: 'Outfit, Inter, system-ui, sans-serif',
              size: 12,
              weight: '500'
            },
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
            label: function(context) {
              let label = context.dataset.label || '';
              if (label) {
                label += ': ';
              }
              if (context.parsed.y !== null) {
                if (isThroughput) {
                  label += context.parsed.y.toLocaleString(undefined, { maximumFractionDigits: 1 }) + ' ops/s';
                } else {
                  label += context.parsed.y.toLocaleString(undefined, { maximumFractionDigits: 4 }) + ' ms';
                }
              }
              return label;
            }
          }
        }
      },
      scales: {
        x: {
          grid: {
            color: 'rgba(255, 255, 255, 0.05)'
          },
          ticks: {
            color: '#94a3b8',
            font: {
              family: 'Outfit, Inter, system-ui, sans-serif'
            }
          }
        },
        y: {
          type: isThroughput ? 'linear' : 'logarithmic', // Log scale for latency handles the massive gap between Redis and MySQL JPA
          grid: {
            color: 'rgba(255, 255, 255, 0.05)'
          },
          ticks: {
            color: '#94a3b8',
            font: {
              family: 'Outfit, Inter, system-ui, sans-serif'
            },
            callback: function(value) {
              if (isThroughput) {
                return value.toLocaleString();
              } else {
                // Logarithmic scale ticks cleanup
                if (value === 0.0001 || value === 0.001 || value === 0.01 || value === 0.1 || value === 1 || value === 10 || value === 100 || value === 1000 || value === 10000) {
                  return value + ' ms';
                }
                return value.toString();
              }
            }
          },
          title: {
            display: true,
            text: isThroughput ? '초당 처리량 (높을수록 좋음)' : '평균 지연 시간 (낮을수록 좋음 - 로그 스케일)',
            color: '#94a3b8',
            font: {
              family: 'Outfit, Inter, system-ui, sans-serif',
              size: 11
            }
          }
        }
      }
    }
  });
};

watch(() => props.results, () => {
  renderChart();
}, { deep: true });

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

.tab-group {
  display: flex;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 4px;
}

.tab-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.tab-btn:hover {
  color: #f8fafc;
}

.tab-btn.active {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.canvas-wrapper {
  position: relative;
  flex-grow: 1;
  min-height: 320px;
  width: 100%;
}
</style>
