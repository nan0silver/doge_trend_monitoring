---
layout: home
title: "도지코인 트렌드 대시보드"
---


이 사이트는 '도지코인'에 관한 뉴스 트렌드와 LLM 기반 분석을 매일 제공합니다.

## 최근 분석

아래 목록에서 날짜별 분석 결과를 확인할 수 있습니다.

<div class="chart-container" style="width: 80%; margin: auto;">
    <canvas id="priceChart"></canvas>
</div>

<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script>
fetch("https://api.coingecko.com/api/v3/coins/dogecoin/market_chart?vs_currency=usd&days=7")
  .then(response => response.json())
  .then(data => {
    const ctx = document.getElementById('priceChart').getContext('2d');
    new Chart(ctx, {
      type: 'line',
      data: {
        labels: data.prices.map(item => new Date(item[0]).toLocaleDateString()),
        datasets: [{
          label: "도지코인 가격 (USD)",
          data: data.prices.map(item => item[1]),
          borderColor: "blue",
          fill: false
        }]
      }
    });
  });
</script>