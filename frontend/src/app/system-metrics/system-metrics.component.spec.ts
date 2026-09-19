import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SystemMetricsComponent } from './system-metrics.component';

const NAMES = [
  'jvm.memory.used',
  'jvm.memory.max',
  'process.cpu.usage',
  'jvm.threads.live',
  'jvm.threads.peak',
  'http.server.requests',
];

describe('System metrics', () => {
  let requests: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SystemMetricsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    requests = TestBed.inject(HttpTestingController);
  });
  afterEach(() => requests.verify());

  function load(names: string[] = NAMES) {
    const component = TestBed.createComponent(SystemMetricsComponent).componentInstance;
    requests.expectOne('/actuator/health').flush({ status: 'UP' });
    requests.expectOne('/actuator/metrics').flush({ names });
    return component;
  }

  function flushMetric(
    name: string,
    value: number,
    baseUnit?: string,
    measurements?: Array<{ statistic: string; value: number }>,
  ) {
    requests.expectOne(`/actuator/metrics/${name}`).flush({
      name,
      baseUnit,
      measurements: measurements ?? [{ statistic: 'VALUE', value }],
    });
  }

  it('renders byte metrics in megabytes and keeps the configured card order', () => {
    const component = load();
    flushMetric('jvm.memory.used', 1_977_127_928, 'bytes');
    flushMetric('jvm.memory.max', 3_271_557_117, 'bytes');
    flushMetric('process.cpu.usage', 0.012);
    flushMetric('jvm.threads.live', 25);
    flushMetric('jvm.threads.peak', 26);
    flushMetric('http.server.requests', 49, 'seconds', [
      { statistic: 'COUNT', value: 49 },
      { statistic: 'TOTAL_TIME', value: 4.9 },
      { statistic: 'MAX', value: 0.75 },
    ]);

    const cards = component.cards();
    expect(cards.map((card) => card.name)).toEqual(NAMES);
    expect(cards.map((card) => card.category)).toEqual([
      'Memory',
      'Memory',
      'CPU',
      'Threads',
      'Threads',
      'Traffic',
    ]);
    expect(cards[0].value).toBe('1,885.5');
    expect(cards[0].unit).toBe('MB');
    expect(cards[1].value).toBe('3,120');
    expect(cards[2].value).toBe('1.2');
    expect(cards[2].unit).toBe('%');
    expect(cards[3].detail).toBe('96% of the 26 thread peak');
    expect(cards[5].detail).toBe('Average 100 ms · slowest 750 ms');
    expect(component.healthy()).toBe(true);
    expect(component.megabytes(1_048_576)).toBe('1 MB');
  });

  it('summarises memory usage as a percentage of the JVM limit', () => {
    const component = load(['jvm.memory.used', 'jvm.memory.max']);
    flushMetric('jvm.memory.used', 512 * 1024 * 1024, 'bytes');
    flushMetric('jvm.memory.max', 1024 * 1024 * 1024, 'bytes');

    expect(component.memoryUsage()).toEqual({
      used: 536_870_912,
      max: 1_073_741_824,
      free: 536_870_912,
      percent: 50,
    });
    expect(component.cards()[0].percent).toBe(50);
    expect(component.cards()[0].detail).toBe('50.0% of the 1,024 MB limit · 512 MB free');
    expect(component.cards()[1].detail).toBe('1 GB');
  });

  it('surfaces an error message and marks degraded health', () => {
    const component = TestBed.createComponent(SystemMetricsComponent).componentInstance;
    requests.expectOne('/actuator/health').flush({ status: 'DOWN' });
    requests
      .expectOne('/actuator/metrics')
      .flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(component.loading()).toBe(false);
    expect(component.error()).toContain('Metrics are unavailable.');
    expect(component.healthy()).toBe(false);
    expect(component.cards()).toEqual([]);
  });
});
