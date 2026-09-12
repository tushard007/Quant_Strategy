import { Component, Input } from '@angular/core';

export interface BreadthChartSeries {
  label: string;
  color: string;
  values: Array<number | null>;
}

interface RenderedSeries extends BreadthChartSeries {
  path: string;
}

@Component({
  selector: 'app-breadth-line-chart',
  templateUrl: './breadth-line-chart.component.html',
  styleUrl: './breadth-line-chart.component.scss',
})
export class BreadthLineChartComponent {
  @Input() labels: string[] = [];
  @Input() series: BreadthChartSeries[] = [];
  @Input() threshold: number | null = null;
  @Input() normalized = false;
  @Input() accessibleLabel = 'Historical market breadth chart';

  readonly width = 760;
  readonly height = 250;
  readonly left = 54;
  readonly right = 18;
  readonly top = 18;
  readonly bottom = 38;

  get renderedSeries(): RenderedSeries[] {
    const transformed = this.series.map((item) => ({
      ...item,
      values: this.transform(item.values),
    }));
    const domain = this.domain(transformed.flatMap((item) => item.values));
    return transformed.map((item) => ({
      ...item,
      path: this.path(item.values, domain.min, domain.max),
    }));
  }

  get domainLabels(): string[] {
    const values = this.series.flatMap((item) => this.transform(item.values)).filter(this.isNumber);
    if (!values.length) return ['—', '—', '—'];
    const min = Math.min(
      ...values,
      ...(this.threshold === null || this.normalized ? [] : [this.threshold]),
    );
    const max = Math.max(
      ...values,
      ...(this.threshold === null || this.normalized ? [] : [this.threshold]),
    );
    const padding = min === max ? Math.max(Math.abs(min) * 0.1, 1) : (max - min) * 0.08;
    const low = min - padding;
    const high = max + padding;
    return [high, (high + low) / 2, low].map((value) => this.format(value));
  }

  get dateLabels(): string[] {
    if (!this.labels.length) return [];
    const indices = [
      ...new Set([0, Math.floor((this.labels.length - 1) / 2), this.labels.length - 1]),
    ];
    return indices.map((index) => this.labels[index]);
  }

  get thresholdY(): number | null {
    if (this.threshold === null || this.normalized) return null;
    const transformed = this.series.map((item) => ({
      ...item,
      values: this.transform(item.values),
    }));
    const domain = this.domain(transformed.flatMap((item) => item.values));
    return this.y(this.threshold, domain.min, domain.max);
  }

  private transform(values: Array<number | null>): Array<number | null> {
    if (!this.normalized) return values;
    const first = values.find(this.isNumber);
    if (first === undefined) return values;
    if (first === 0) {
      const numeric = values.filter(this.isNumber);
      const low = Math.min(...numeric);
      const high = Math.max(...numeric);
      return values.map((value) =>
        value === null ? null : high === low ? 0 : ((value - low) / (high - low)) * 100,
      );
    }
    return values.map((value) =>
      value === null ? null : ((value - first) / Math.abs(first)) * 100,
    );
  }

  private domain(values: Array<number | null>): { min: number; max: number } {
    const numeric = values.filter(this.isNumber);
    if (this.threshold !== null && !this.normalized) numeric.push(this.threshold);
    if (!numeric.length) return { min: 0, max: 1 };
    const rawMin = Math.min(...numeric);
    const rawMax = Math.max(...numeric);
    const padding =
      rawMin === rawMax ? Math.max(Math.abs(rawMin) * 0.1, 1) : (rawMax - rawMin) * 0.08;
    return { min: rawMin - padding, max: rawMax + padding };
  }

  private path(values: Array<number | null>, min: number, max: number): string {
    const plotWidth = this.width - this.left - this.right;
    const denominator = Math.max(values.length - 1, 1);
    let drawing = false;
    return values
      .map((value, index) => {
        if (value === null || !Number.isFinite(value)) {
          drawing = false;
          return '';
        }
        const x = this.left + (index / denominator) * plotWidth;
        const y = this.y(value, min, max);
        const command = drawing ? 'L' : 'M';
        drawing = true;
        return `${command}${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .filter(Boolean)
      .join(' ');
  }

  private y(value: number, min: number, max: number): number {
    const plotHeight = this.height - this.top - this.bottom;
    return this.top + ((max - value) / Math.max(max - min, 1)) * plotHeight;
  }

  private format(value: number): string {
    return Math.abs(value) >= 1000 ? Math.round(value).toLocaleString('en-IN') : value.toFixed(1);
  }

  private readonly isNumber = (value: number | null): value is number =>
    value !== null && Number.isFinite(value);
}
