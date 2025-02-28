---
layout: home
title: "도지코인 트렌드 대시보드"
---


이 사이트는 '도지코인'에 관한 뉴스 트렌드와 LLM 기반 분석을 매일 제공합니다.

## 최근 분석

아래 목록에서 날짜별 분석 결과를 확인할 수 있습니다.

<div class="chart-container" style="width: 800px; margin: auto;">
    <canvas id="priceChart"></canvas>
</div>

<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script>
Promise.all([
  fetch("https://api.coingecko.com/api/v3/coins/dogecoin/market_chart?vs_currency=usd&days=7").then(response => response.json()),
  fetch("https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=7").then(response => response.json())
])
.then(([dogeData, btcData]) => {
  const ctx = document.getElementById('priceChart').getContext('2d');
  
  // 날짜 레이블은 도지코인 데이터에서 가져옵니다
  const labels = dogeData.prices.map(item => new Date(item[0]).toLocaleDateString());
  
  // 비트코인 가격은 굉장히 높기 때문에, 데이터 스케일 조정을 위해 별도의 Y축을 사용합니다
  new Chart(ctx, {
    type: 'line',
    data: {
      labels: labels,
      datasets: [
        {
          label: "도지코인 가격 (USD)",
          data: dogeData.prices.map(item => item[1]),
          borderColor: "blue",
          fill: false,
          yAxisID: 'y-doge'
        },
        {
          label: "비트코인 가격 (USD)",
          data: btcData.prices.map(item => item[1]),
          borderColor: "orange",
          fill: false,
          yAxisID: 'y-btc'
        }
      ]
    },
    options: {
      scales: {
        'y-doge': {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: '도지코인 가격 (USD)'
          }
        },
        'y-btc': {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: '비트코인 가격 (USD)'
          },
          grid: {
            drawOnChartArea: false // 그리드 라인 중복 방지
          }
        }
      }
    }
  });
});
</script>