<template>
  <div class="dashboard">
    <header class="dashboard-header">
      <div class="title-container">
        <span class="badge">Benchmark System</span>
        <h1>MySQL vs Redis 성능 비교 데모</h1>
        <p class="subtitle">다양한 데이터 액세스 계층과 작업 방식에 따른 데이터베이스 속도와 처리량 분석</p>
      </div>
    </header>

    <main class="dashboard-grid">
      <!-- 1. Configuration Panel -->
      <section class="card config-card">
        <h2>벤치마크 구성</h2>
        
        <div class="form-group">
          <label>작업 횟수 (Count): <span class="value-highlight">{{ config.count.toLocaleString() }} ops</span></label>
          <input 
            type="range" 
            v-model.number="config.count" 
            min="100" 
            max="10000" 
            step="100"
            class="slider"
          />
          <div class="slider-labels">
            <span>100</span>
            <span>5,000</span>
            <span>10,000</span>
          </div>
          <p v-if="config.count > 3000" class="warning-text">
            ⚠️ 3,000개 이상의 단건 JPA 작업은 수 초 이상 소요될 수 있습니다.
          </p>
        </div>

        <div class="form-group">
          <label>페이로드 크기 (Payload Size): <span class="value-highlight">{{ config.payloadSize }} bytes</span></label>
          <input 
            type="range" 
            v-model.number="config.payloadSize" 
            min="10" 
            max="5000" 
            step="10"
            class="slider"
          />
          <div class="slider-labels">
            <span>10B</span>
            <span>2.5KB</span>
            <span>5KB</span>
          </div>
        </div>

        <div class="form-group">
          <label>대상 환경 (Targets)</label>
          <div class="checkbox-group">
            <label v-for="target in availableTargets" :key="target.value" class="checkbox-label">
              <input type="checkbox" v-model="config.selectedTargets" :value="target.value">
              <span class="custom-checkbox"></span>
              <span class="label-text">{{ target.name }}</span>
            </label>
          </div>
        </div>

        <div class="form-group">
          <label>테스트 작업 (Operations)</label>
          <div class="checkbox-group">
            <label v-for="op in availableOperations" :key="op.value" class="checkbox-label">
              <input type="checkbox" v-model="config.selectedOperations" :value="op.value">
              <span class="custom-checkbox"></span>
              <span class="label-text">{{ op.name }}</span>
            </label>
          </div>
        </div>

        <button 
          @click="startBenchmark" 
          :disabled="isLoading || !isConfigValid" 
          class="btn-primary"
        >
          <span v-if="isLoading" class="spinner-small"></span>
          <span>{{ isLoading ? '벤치마크 실행 중...' : '벤치마크 시작하기' }}</span>
        </button>
      </section>

      <!-- 2. Main Visualization & Insights -->
      <div class="results-visual-container">
        <!-- Error Alert -->
        <div v-if="errorMessage" class="error-alert">
          <p>{{ errorMessage }}</p>
        </div>

        <!-- Initial State -->
        <div v-if="!isLoading && (!results || results.length === 0)" class="empty-state card">
          <div class="empty-icon">📊</div>
          <h3>벤치마크 데이터가 없습니다</h3>
          <p>좌측 옵션을 설정하고 '벤치마크 시작하기' 버튼을 클릭하여 테스트를 수행하세요.</p>
        </div>

        <!-- Loading State -->
        <div v-if="isLoading" class="loading-state card">
          <div class="spinner-large"></div>
          <h3>성능 분석을 수행하고 있습니다</h3>
          <p>데이터베이스 쓰기/읽기 작업을 수행하며 지연 시간과 처리량을 측정 중입니다...</p>
        </div>

        <!-- Charts & Key Insights Grid -->
        <div v-if="!isLoading && results && results.length > 0" class="visualization-grid">
          <!-- Chart Component -->
          <div class="chart-wrapper-card">
            <ChartComponent :results="results" />
          </div>

          <!-- Dynamic Insights -->
          <section class="card insights-card">
            <h2>핵심 분석 결과</h2>
            <div class="insights-list">
              <div v-for="(insight, idx) in calculatedInsights" :key="idx" :class="['insight-item', insight.type]">
                <div class="insight-icon">
                  <span v-if="insight.type === 'success'">🚀</span>
                  <span v-else-if="insight.type === 'warning'">⚠️</span>
                  <span v-else>💡</span>
                </div>
                <div class="insight-content">
                  <p>{{ insight.text }}</p>
                </div>
              </div>
              <div v-if="calculatedInsights.length === 0" class="no-insights">
                비교 가능한 결과가 충분하지 않습니다. 다양한 타겟과 작업을 선택해 보세요.
              </div>
            </div>
          </section>
        </div>
      </div>
    </main>

    <!-- 3. Detailed Stats Table -->
    <section v-if="!isLoading && results && results.length > 0" class="card table-card">
      <div class="table-header">
        <h2>상세 성능 지표</h2>
        <span class="info-tag">Count: {{ resultsCount }} ops | Payload: {{ resultsPayload }} bytes</span>
      </div>
      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th>대상</th>
              <th>작업 유형</th>
              <th>초당 처리량 (Throughput)</th>
              <th>평균 지연 (Avg)</th>
              <th>최소 지연 (Min)</th>
              <th>최대 지연 (Max)</th>
              <th>총 소요 시간 (Total)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in results" :key="row.target + '-' + row.operation">
              <td>
                <span :class="['badge-target', row.target]">
                  {{ getTargetName(row.target) }}
                </span>
              </td>
              <td>{{ getOperationName(row.operation) }}</td>
              <td class="numeric font-outfit">{{ row.opsPerSec.toLocaleString(undefined, { maximumFractionDigits: 1 }) }} ops/s</td>
              <td class="numeric font-outfit">{{ row.avgTimeMs.toLocaleString(undefined, { maximumFractionDigits: 4 }) }} ms</td>
              <td class="numeric font-outfit">{{ row.minTimeMs.toLocaleString(undefined, { maximumFractionDigits: 4 }) }} ms</td>
              <td class="numeric font-outfit">{{ row.maxTimeMs.toLocaleString(undefined, { maximumFractionDigits: 4 }) }} ms</td>
              <td class="numeric font-outfit">{{ row.totalTimeMs.toLocaleString(undefined, { maximumFractionDigits: 1 }) }} ms</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import ChartComponent from './components/ChartComponent.vue';

const config = reactive({
  count: 1000,
  payloadSize: 100,
  selectedTargets: ['MYSQL_JPA', 'MYSQL_JDBC', 'REDIS', 'REDIS_PIPELINE'],
  selectedOperations: ['WRITE_SINGLE', 'WRITE_BATCH', 'READ_SINGLE', 'READ_BATCH']
});

const isLoading = ref(false);
const results = ref([]);
const resultsCount = ref(1000);
const resultsPayload = ref(100);
const errorMessage = ref('');

const availableTargets = [
  { value: 'MYSQL_JPA', name: 'MySQL (JPA)' },
  { value: 'MYSQL_JDBC', name: 'MySQL (JDBC Template)' },
  { value: 'REDIS', name: 'Redis (Standard)' },
  { value: 'REDIS_PIPELINE', name: 'Redis (Pipeline)' }
];

const availableOperations = [
  { value: 'WRITE_SINGLE', name: '단건 쓰기 (1 by 1)' },
  { value: 'WRITE_BATCH', name: '배치 쓰기 (Bulk)' },
  { value: 'READ_SINGLE', name: '단건 읽기 (1 by 1)' },
  { value: 'READ_BATCH', name: '배치 읽기 (Bulk)' }
];

const targetNames = {
  'MYSQL_JPA': 'MySQL (JPA)',
  'MYSQL_JDBC': 'MySQL (JDBC)',
  'REDIS': 'Redis (Standard)',
  'REDIS_PIPELINE': 'Redis (Pipeline)'
};

const operationNames = {
  'WRITE_SINGLE': '단건 쓰기',
  'WRITE_BATCH': '배치 쓰기',
  'READ_SINGLE': '단건 읽기',
  'READ_BATCH': '배치 읽기'
};

const getTargetName = (t) => targetNames[t] || t;
const getOperationName = (o) => operationNames[o] || o;

const isConfigValid = computed(() => {
  return config.selectedTargets.length > 0 && config.selectedOperations.length > 0;
});

const startBenchmark = async () => {
  if (!isConfigValid.value) return;
  
  isLoading.value = true;
  errorMessage.value = '';
  results.value = [];

  try {
    const response = await fetch('/api/benchmark/run', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        count: config.count,
        payloadSize: config.payloadSize,
        targets: config.selectedTargets,
        operations: config.selectedOperations
      })
    });

    if (!response.ok) {
      throw new Error(`서버 오류: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    
    // Sort results to keep a nice logical order in the table
    const targetOrder = ['MYSQL_JPA', 'MYSQL_JDBC', 'REDIS', 'REDIS_PIPELINE'];
    const opOrder = ['WRITE_SINGLE', 'WRITE_BATCH', 'READ_SINGLE', 'READ_BATCH'];
    
    data.results.sort((a, b) => {
      const targetDiff = targetOrder.indexOf(a.target) - targetOrder.indexOf(b.target);
      if (targetDiff !== 0) return targetDiff;
      return opOrder.indexOf(a.operation) - opOrder.indexOf(b.operation);
    });

    results.value = data.results;
    resultsCount.value = data.count;
    resultsPayload.value = data.payloadSize;
  } catch (error) {
    console.error('Benchmark error:', error);
    errorMessage.value = `벤치마크 수행 중 오류가 발생했습니다: ${error.message}`;
  } finally {
    isLoading.value = false;
  }
};

const calculatedInsights = computed(() => {
  if (!results.value || results.value.length === 0) return [];
  
  const list = [];
  
  // 1. 단건 쓰기 비교
  const writeSingle = results.value.filter(r => r.operation === 'WRITE_SINGLE' && r.opsPerSec > 0);
  if (writeSingle.length > 1) {
    writeSingle.sort((a, b) => b.opsPerSec - a.opsPerSec);
    const fastest = writeSingle[0];
    const slowest = writeSingle[writeSingle.length - 1];
    const ratio = fastest.opsPerSec / slowest.opsPerSec;
    list.push({
      type: 'success',
      text: `단건 쓰기(WRITE_SINGLE)에서 ${getTargetName(fastest.target)}가 ${getTargetName(slowest.target)}보다 ${ratio.toFixed(1)}배 더 빠릅니다.`
    });
  }

  // 2. MySQL JPA vs JDBC 배치 쓰기 비교
  const mysqlJpaBatchWrite = results.value.find(r => r.target === 'MYSQL_JPA' && r.operation === 'WRITE_BATCH');
  const mysqlJdbcBatchWrite = results.value.find(r => r.target === 'MYSQL_JDBC' && r.operation === 'WRITE_BATCH');
  if (mysqlJpaBatchWrite && mysqlJdbcBatchWrite && mysqlJpaBatchWrite.opsPerSec > 0 && mysqlJdbcBatchWrite.opsPerSec > 0) {
    const ratio = mysqlJdbcBatchWrite.opsPerSec / mysqlJpaBatchWrite.opsPerSec;
    if (ratio > 1.2) {
      list.push({
        type: 'info',
        text: `MySQL 배치 쓰기 시 JDBC가 JPA(saveAll)보다 ${ratio.toFixed(1)}배 더 빠릅니다. (JPA의 Hibernate 영속성 컨텍스트 처리 오버헤드 부재)`
      });
    }
  }

  // 3. Redis Single vs Pipeline 읽기 비교
  const redisSingleRead = results.value.find(r => r.target === 'REDIS' && r.operation === 'READ_SINGLE');
  const redisPipelineRead = results.value.find(r => r.target === 'REDIS_PIPELINE' && r.operation === 'READ_SINGLE');
  if (redisSingleRead && redisPipelineRead && redisSingleRead.opsPerSec > 0 && redisPipelineRead.opsPerSec > 0) {
    const ratio = redisPipelineRead.opsPerSec / redisSingleRead.opsPerSec;
    if (ratio > 1.2) {
      list.push({
        type: 'info',
        text: `Redis 읽기 시 Pipelining 적용으로 성능이 ${ratio.toFixed(1)}배 증가했습니다. (네트워크 RTT(왕복 속도)의 영향 최소화)`
      });
    }
  }

  // 4. Redis Pipeline vs MySQL JDBC 배치 쓰기 비교
  const redisPipeBatchWrite = results.value.find(r => r.target === 'REDIS_PIPELINE' && r.operation === 'WRITE_BATCH');
  const mysqlJdbcBatchWrite2 = results.value.find(r => r.target === 'MYSQL_JDBC' && r.operation === 'WRITE_BATCH');
  if (redisPipeBatchWrite && mysqlJdbcBatchWrite2 && redisPipeBatchWrite.opsPerSec > 0 && mysqlJdbcBatchWrite2.opsPerSec > 0) {
    const ratio = redisPipeBatchWrite.opsPerSec / mysqlJdbcBatchWrite2.opsPerSec;
    list.push({
      type: 'warning',
      text: `메모리 기반의 Redis (Pipeline)는 디스크 커밋 작업이 필요한 MySQL (JDBC Batch)보다 쓰기 성능이 ${ratio.toFixed(1)}배 더 우수합니다.`
    });
  }

  return list;
});
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
  text-align: left;
}

.dashboard-header {
  margin-bottom: 8px;
}

.badge {
  background: rgba(99, 102, 241, 0.15);
  color: #818cf8;
  padding: 4px 10px;
  border-radius: 99px;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  border: 1px solid rgba(99, 102, 241, 0.2);
  display: inline-block;
  margin-bottom: 8px;
}

.dashboard-header h1 {
  margin: 0;
  font-size: 2.25rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  background: linear-gradient(135deg, #ffffff 40%, #a5b4fc 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.subtitle {
  margin: 6px 0 0 0;
  color: #94a3b8;
  font-size: 1rem;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 24px;
  align-items: start;
}

@media (max-width: 1024px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

/* Glassmorphic Card base */
.card {
  background: rgba(17, 24, 39, 0.45);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
}

.card h2 {
  margin-top: 0;
  margin-bottom: 20px;
  font-size: 1.25rem;
  font-weight: 600;
  color: #f8fafc;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  padding-bottom: 12px;
}

/* Config Card Specifics */
.config-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  color: #94a3b8;
  font-size: 0.88rem;
  font-weight: 500;
  display: flex;
  justify-content: space-between;
}

.value-highlight {
  color: #60a5fa;
  font-weight: 600;
}

.slider {
  -webkit-appearance: none;
  width: 100%;
  height: 6px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.1);
  outline: none;
}

.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #3b82f6;
  cursor: pointer;
  transition: transform 0.1s;
}

.slider::-webkit-slider-thumb:hover {
  transform: scale(1.2);
}

.slider-labels {
  display: flex;
  justify-content: space-between;
  color: #64748b;
  font-size: 0.75rem;
}

.warning-text {
  color: #f59e0b;
  font-size: 0.78rem;
  margin: 4px 0 0 0;
}

/* Checkbox group styling */
.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(15, 23, 42, 0.3);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.03);
}

.checkbox-label {
  display: flex;
  align-items: center;
  position: relative;
  cursor: pointer;
  user-select: none;
  font-size: 0.88rem;
  color: #cbd5e1;
}

.checkbox-label input {
  position: absolute;
  opacity: 0;
  cursor: pointer;
  height: 0;
  width: 0;
}

.custom-checkbox {
  height: 18px;
  width: 18px;
  background-color: rgba(255,255,255,0.05);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 4px;
  margin-right: 10px;
  display: inline-block;
  transition: all 0.2s;
  flex-shrink: 0;
}

.checkbox-label:hover input ~ .custom-checkbox {
  border-color: rgba(255, 255, 255, 0.3);
}

.checkbox-label input:checked ~ .custom-checkbox {
  background-color: #3b82f6;
  border-color: #3b82f6;
}

.custom-checkbox:after {
  content: "";
  display: none;
  margin-left: 5px;
  margin-top: 2px;
  width: 5px;
  height: 9px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.checkbox-label input:checked ~ .custom-checkbox:after {
  display: block;
}

.label-text {
  transition: color 0.2s;
}

.checkbox-label input:checked ~ .label-text {
  color: #ffffff;
  font-weight: 500;
}

/* Primary Button */
.btn-primary {
  width: 100%;
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  color: white;
  border: none;
  font-weight: 600;
  padding: 12px;
  font-size: 0.95rem;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.4);
  background: linear-gradient(135deg, #60a5fa 0%, #2563eb 100%);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

/* Loading Spinner */
.spinner-small {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Results Section Visuals */
.results-visual-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 480px;
}

.error-alert {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.25);
  color: #fca5a5;
  padding: 14px 18px;
  border-radius: 12px;
  font-size: 0.9rem;
}

.error-alert p {
  margin: 0;
}

.empty-state {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px;
  color: #64748b;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 16px;
}

.empty-state h3 {
  margin: 0 0 8px 0;
  color: #e2e8f0;
}

.empty-state p {
  margin: 0;
  max-width: 380px;
  font-size: 0.9rem;
}

.loading-state {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px;
}

.spinner-large {
  width: 48px;
  height: 48px;
  border: 3.5px solid rgba(255, 255, 255, 0.08);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s cubic-bezier(0.55, 0.15, 0.45, 0.85) infinite;
  margin-bottom: 24px;
}

.loading-state h3 {
  margin: 0 0 8px 0;
  color: #f8fafc;
}

.loading-state p {
  margin: 0;
  color: #94a3b8;
  font-size: 0.9rem;
  max-width: 400px;
}

.visualization-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;
}

@media (max-width: 1200px) {
  .visualization-grid {
    grid-template-columns: 1fr;
  }
}

.chart-wrapper-card {
  min-height: 400px;
}

/* Insights Card Specifics */
.insights-card {
  display: flex;
  flex-direction: column;
}

.insights-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex-grow: 1;
}

.insight-item {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 0.85rem;
  line-height: 1.4;
  border-left: 3px solid transparent;
}

.insight-item.success {
  background: rgba(16, 185, 129, 0.08);
  border-left-color: #10b981;
  color: #a7f3d0;
}

.insight-item.info {
  background: rgba(59, 130, 246, 0.08);
  border-left-color: #3b82f6;
  color: #bfdbfe;
}

.insight-item.warning {
  background: rgba(245, 158, 11, 0.08);
  border-left-color: #f59e0b;
  color: #fde68a;
}

.insight-icon {
  font-size: 1.1rem;
  flex-shrink: 0;
  margin-top: -1px;
}

.insight-content p {
  margin: 0;
}

.no-insights {
  color: #64748b;
  text-align: center;
  padding: 24px;
  font-size: 0.88rem;
}

/* Detailed Stats Table Card */
.table-card {
  margin-top: 8px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  margin-bottom: 20px;
  padding-bottom: 12px;
}

.table-header h2 {
  margin: 0;
  border: none;
  padding: 0;
}

.info-tag {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.06);
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 0.78rem;
  color: #94a3b8;
  font-weight: 500;
}

.table-responsive {
  width: 100%;
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 0.88rem;
}

th {
  color: #94a3b8;
  font-weight: 600;
  padding: 12px 16px;
  border-bottom: 1.5px solid rgba(255, 255, 255, 0.08);
  text-transform: uppercase;
  font-size: 0.75rem;
  letter-spacing: 0.05em;
}

td {
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  color: #cbd5e1;
}

tr:hover td {
  background: rgba(255, 255, 255, 0.015);
  color: #ffffff;
}

.numeric {
  text-align: right;
}

.font-outfit {
  font-family: 'Outfit', monospace;
  font-weight: 500;
}

/* Badges for targets */
.badge-target {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
}

.badge-target.MYSQL_JPA {
  background: rgba(239, 68, 68, 0.12);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.badge-target.MYSQL_JDBC {
  background: rgba(245, 158, 11, 0.12);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.badge-target.REDIS {
  background: rgba(16, 185, 129, 0.12);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.badge-target.REDIS_PIPELINE {
  background: rgba(139, 92, 246, 0.12);
  color: #a78bfa;
  border: 1px solid rgba(139, 92, 246, 0.2);
}
</style>
