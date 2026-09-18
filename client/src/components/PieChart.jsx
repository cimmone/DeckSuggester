import { useEffect, useRef } from 'react';
import Highcharts from 'highcharts';
import { COLOR_PALETTE, METRICS } from '../lib/filters';

export function PieChart({ metric, values, onSelect }) {
  const chartElement = useRef(null);

  useEffect(() => {
    if (!chartElement.current || !Object.keys(values || {}).length) return undefined;
    const chart = Highcharts.chart(chartElement.current, {
      chart: {
        type: 'pie', backgroundColor: 'transparent', height: 330,
        style: { fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif' },
      },
      title: { text: undefined },
      credits: { enabled: false },
      accessibility: { enabled: false },
      tooltip: {
        pointFormat: '<b>{point.y}</b> ({point.percentage:.1f}%)',
        backgroundColor: '#15231f', borderWidth: 0, borderRadius: 10,
        style: { color: '#f8f5ec' },
      },
      legend: {
        align: 'center', verticalAlign: 'bottom',
        itemStyle: { color: '#52605a', fontSize: '12px', fontWeight: '500' },
        itemHoverStyle: { color: '#16251f' },
      },
      plotOptions: {
        pie: {
          allowPointSelect: true, cursor: 'pointer', borderColor: '#fbfaf5',
          borderWidth: 3, innerSize: '56%', showInLegend: true,
          dataLabels: { enabled: false },
          point: { events: { click() { onSelect(metric, this.name); } } },
        },
      },
      series: [{
        name: METRICS[metric].label,
        colorByPoint: true,
        data: Object.entries(values).map(([name, value]) => ({
          name, y: value, color: COLOR_PALETTE[name],
        })),
      }],
    });
    return () => chart.destroy();
  }, [metric, values, onSelect]);

  if (!Object.keys(values || {}).length) {
    return <div className="chart-empty">{METRICS[metric].empty}</div>;
  }
  return <div ref={chartElement} aria-label={`${METRICS[metric].label} pie chart`} />;
}
