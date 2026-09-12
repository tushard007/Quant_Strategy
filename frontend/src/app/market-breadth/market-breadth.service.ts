import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BreadthAlert,
  BreadthAlertFilters,
  BreadthRecalculationRequest,
  BreadthRecalculationResponse,
  BreadthReferenceDataReport,
  BreadthScoreConfiguration,
  BreadthScoreConfigurationRequest,
  BreadthSnapshot,
  BreadthUniverse,
  SectorBreadth,
} from './market-breadth.models';

export * from './market-breadth.models';

@Injectable({ providedIn: 'root' })
export class MarketBreadthService {
  private readonly baseUrl = '/api/market-breadth';
  constructor(private readonly http: HttpClient) {}
  checkReferenceData(): Observable<BreadthReferenceDataReport> {
    return this.http.get<BreadthReferenceDataReport>(`${this.baseUrl}/reference-data/check`);
  }
  getLatest(universe: BreadthUniverse, asOf?: string): Observable<BreadthSnapshot> {
    return this.http.get<BreadthSnapshot>(`${this.baseUrl}/latest`, {
      params: asOf ? { universe, asOf } : { universe },
    });
  }
  getHistory(
    universe: BreadthUniverse,
    from: string,
    to: string,
    limit = 500,
  ): Observable<BreadthSnapshot[]> {
    return this.http.get<BreadthSnapshot[]>(`${this.baseUrl}/history`, {
      params: { universe, from, to, limit: String(limit) },
    });
  }
  getSectors(universe: BreadthUniverse, asOf?: string): Observable<SectorBreadth> {
    return this.http.get<SectorBreadth>(`${this.baseUrl}/sectors`, {
      params: asOf ? { universe, asOf } : { universe },
    });
  }
  getConfiguration(): Observable<BreadthScoreConfiguration> {
    return this.http.get<BreadthScoreConfiguration>(`${this.baseUrl}/config`);
  }
  updateConfiguration(
    request: BreadthScoreConfigurationRequest,
  ): Observable<BreadthScoreConfiguration> {
    return this.http.put<BreadthScoreConfiguration>(`${this.baseUrl}/config`, request);
  }
  recalculate(request: BreadthRecalculationRequest): Observable<BreadthRecalculationResponse> {
    return this.http.post<BreadthRecalculationResponse>(`${this.baseUrl}/recalculate`, request);
  }
  exportExcel(universe: BreadthUniverse, from: string, to: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, {
      params: { universe, from, to },
      responseType: 'blob',
    });
  }
  getAlerts(filters: BreadthAlertFilters = {}): Observable<BreadthAlert[]> {
    let params = new HttpParams().set('limit', String(filters.limit ?? 100));
    if (filters.universe) params = params.set('universe', filters.universe);
    if (filters.alertType) params = params.set('alertType', filters.alertType);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<BreadthAlert[]>(`${this.baseUrl}/alerts`, { params });
  }
}
