import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Subscription, forkJoin, switchMap } from 'rxjs';

interface MetricIndex {
  names: string[];
}
interface MetricMeasurement {
  statistic: string;
  value: number;
}
interface MetricResponse {
  name: string;
  measurements: MetricMeasurement[];
  baseUnit?: string;
}
interface HealthResponse {
  status: string;
  components?: Record<string, { status: string; details?: Record<string, unknown> }>;
}

type MetricTone = 'memory' | 'cpu' | 'threads' | 'traffic';
interface MetricDefinition {
  label: string;
  description: string;
  icon: string;
  tone: MetricTone;
}

/** Metric cards rendered by the template, in the order declared below. */
const METRIC_DEFINITIONS: Record<string, MetricDefinition> = {
  'jvm.memory.used': {
    label: 'JVM memory used',
    description: 'Heap and non-heap memory currently allocated.',
    icon: '▣',
    tone: 'memory',
  },
  'jvm.memory.max': {
    label: 'JVM memory maximum',
    description: 'Upper limit the JVM may allocate.',
    icon: '▤',
    tone: 'memory',
  },
  'process.cpu.usage': {
    label: 'Process CPU usage',
    description: 'Share of CPU capacity used by this application.',
    icon: '◔',
    tone: 'cpu',
  },
  'system.cpu.usage': {
    label: 'System CPU usage',
    description: 'Share of CPU capacity used by the whole host.',
    icon: '◕',
    tone: 'cpu',
  },
  'jvm.threads.live': {
    label: 'Live threads',
    description: 'Daemon and non-daemon threads running now.',
    icon: '≡',
    tone: 'threads',
  },
  'jvm.threads.peak': {
    label: 'Peak threads',
    description: 'Highest thread count since startup.',
    icon: '⋀',
    tone: 'threads',
  },
  'http.server.requests': {
    label: 'HTTP server requests',
    description: 'Requests served since startup.',
    icon: '↔',
    tone: 'traffic',
  },
};
const METRIC_ORDER = Object.keys(METRIC_DEFINITIONS);
const CATEGORY_LABELS: Record<MetricTone, string> = {
  memory: 'Memory',
  cpu: 'CPU',
  threads: 'Threads',
  traffic: 'Traffic',
};
const BYTES_PER_MB = 1024 * 1024;

/** Static card presentation values, resolved before the measurement is formatted. */
type CardBase = MetricDefinition & { category: string };

export interface MetricCard {
  name: string;
  label: string;
  description: string;
  icon: string;
  tone: MetricTone;
  /** Short badge shown at the top of the card, e.g. Memory or CPU. */
  category: string;
  value: string;
  unit: string;
  detail: string | null;
  /** Meter fill percentage, or null when the metric has no natural ceiling. */
  percent: number | null;
}

@Component({
  selector: 'app-system-metrics',
  imports: [DatePipe],
  templateUrl: './system-metrics.component.html',
  styleUrl: './system-metrics.component.scss',
})
export class SystemMetricsComponent implements OnDestroy {
  private readonly http = inject(HttpClient);
  private subscription?: Subscription;
  readonly metrics = signal<MetricResponse[]>([]);
  readonly health = signal<HealthResponse | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly updatedAt = signal<Date | null>(null);
  readonly labels: Record<string, string> = Object.fromEntries(
    Object.entries(METRIC_DEFINITIONS).map(([name, definition]) => [name, definition.label]),
  );

  readonly cards = computed<MetricCard[]>(() =>
    this.metrics()
      .filter((metric) => METRIC_DEFINITIONS[metric.name])
      .slice()
      .sort((left, right) => METRIC_ORDER.indexOf(left.name) - METRIC_ORDER.indexOf(right.name))
      .map((metric) => this.card(metric)),
  );

  readonly memoryUsage = computed(() => {
    const used = this.statistic('jvm.memory.used');
    const max = this.statistic('jvm.memory.max');
    if (used === null || max === null || max <= 0) return null;
    return {
      used,
      max,
      free: Math.max(max - used, 0),
      percent: this.clampPercent((used / max) * 100),
    };
  });

  readonly healthy = computed(() => (this.health()?.status ?? '').toUpperCase() === 'UP');

  constructor() {
    this.load();
  }

  refresh(): void {
    this.error.set(null);
    this.subscription?.unsubscribe();
    this.load();
  }

  megabytes(bytes: number): string {
    return `${(bytes / BYTES_PER_MB).toLocaleString(undefined, { maximumFractionDigits: 1 })} MB`;
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private load(): void {
    this.loading.set(true);
    this.subscription = forkJoin({
      health: this.http.get<HealthResponse>('/actuator/health'),
      index: this.http.get<MetricIndex>('/actuator/metrics'),
    })
      .pipe(
        switchMap(({ health, index }) => {
          this.health.set(health);
          const names = index.names.filter((name) => METRIC_ORDER.includes(name));
          return names.length
            ? forkJoin(
                names.map((name) => this.http.get<MetricResponse>(`/actuator/metrics/${name}`)),
              )
            : [[] as MetricResponse[]];
        }),
      )
      .subscribe({
        next: (metrics) => {
          this.metrics.set(metrics);
          this.updatedAt.set(new Date());
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(
            'Metrics are unavailable. Confirm the application is running and your Superadmin session is valid.',
          );
        },
      });
  }

  private card(metric: MetricResponse): MetricCard {
    const base = METRIC_DEFINITIONS[metric.name];
    const definition: CardBase = { ...base, category: CATEGORY_LABELS[base.tone] };
    const value =
      this.measurement(metric, 'VALUE') ??
      this.measurement(metric, 'COUNT') ??
      metric.measurements?.[0]?.value ??
      0;
    if (definition.tone === 'cpu') return this.cpuCard(definition, metric.name, value);
    if (metric.baseUnit === 'bytes') return this.memoryCard(definition, metric.name, value);
    if (definition.tone === 'threads') return this.threadCard(definition, metric.name, value);
    return this.requestCard(definition, metric, value);
  }

  private cpuCard(definition: CardBase, name: string, value: number): MetricCard {
    const percent = this.clampPercent(value * 100);
    const detail =
      percent >= 85
        ? 'Saturated — investigate running workloads'
        : percent >= 50
          ? 'Busy — sustained load'
          : 'Healthy headroom';
    return { ...definition, name, value: percent.toFixed(1), unit: '%', detail, percent };
  }

  private memoryCard(definition: CardBase, name: string, value: number): MetricCard {
    const usage = this.memoryUsage();
    const used = name === 'jvm.memory.used';
    return {
      ...definition,
      name,
      unit: 'MB',
      value: (value / BYTES_PER_MB).toLocaleString(undefined, { maximumFractionDigits: 1 }),
      percent: used && usage ? usage.percent : null,
      detail:
        used && usage
          ? `${usage.percent.toFixed(1)}% of the ${this.megabytes(usage.max)} limit · ${this.megabytes(usage.free)} free`
          : `${(value / (BYTES_PER_MB * 1024)).toLocaleString(undefined, { maximumFractionDigits: 2 })} GB`,
    };
  }

  private threadCard(definition: CardBase, name: string, value: number): MetricCard {
    const peak = this.statistic('jvm.threads.peak');
    const percent =
      name === 'jvm.threads.live' && peak ? this.clampPercent((value / peak) * 100) : null;
    return {
      ...definition,
      name,
      value: value.toLocaleString(),
      unit: 'threads',
      percent,
      detail:
        percent === null
          ? null
          : `${percent.toFixed(0)}% of the ${peak?.toLocaleString()} thread peak`,
    };
  }

  private requestCard(definition: CardBase, metric: MetricResponse, value: number): MetricCard {
    const totalTime = this.measurement(metric, 'TOTAL_TIME');
    const slowest = this.measurement(metric, 'MAX');
    const details = [
      value > 0 && totalTime !== null
        ? `Average ${((totalTime / value) * 1000).toFixed(0)} ms`
        : null,
      slowest ? `slowest ${(slowest * 1000).toFixed(0)} ms` : null,
    ].filter(Boolean);
    return {
      ...definition,
      name: metric.name,
      value: value.toLocaleString(),
      unit: 'requests',
      percent: null,
      detail: details.length ? details.join(' · ') : null,
    };
  }

  private measurement(metric: MetricResponse, statistic: string): number | null {
    return metric.measurements?.find((entry) => entry.statistic === statistic)?.value ?? null;
  }

  private statistic(name: string): number | null {
    const metric = this.metrics().find((entry) => entry.name === name);
    if (!metric) return null;
    return this.measurement(metric, 'VALUE') ?? metric.measurements?.[0]?.value ?? null;
  }

  private clampPercent(value: number): number {
    return Math.min(Math.max(Number.isFinite(value) ? value : 0, 0), 100);
  }
}
