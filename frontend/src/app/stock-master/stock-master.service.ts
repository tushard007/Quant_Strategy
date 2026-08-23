import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface StockMaster {
  id: number;
  symbol: string;
  nameOfCompany: string;
  series: string;
  isinNumber: string;
  industry: string;
}

export type StockMasterPayload = Omit<StockMaster, 'id'>;
export interface StockMasterImportResult { totalRecords: number; created: number; updated: number; removed: number; message: string; }

@Injectable({ providedIn: 'root' })
export class StockMasterService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/stock-master';

  list(search = ''): Observable<StockMaster[]> {
    const params = search.trim() ? new HttpParams().set('search', search.trim()) : undefined;
    return this.http.get<StockMaster[]>(this.url, { params });
  }
  create(payload: StockMasterPayload): Observable<StockMaster> { return this.http.post<StockMaster>(this.url, payload); }
  update(id: number, payload: StockMasterPayload): Observable<StockMaster> { return this.http.put<StockMaster>(`${this.url}/${id}`, payload); }
  delete(id: number): Observable<void> { return this.http.delete<void>(`${this.url}/${id}`); }
  importCsv(file: File): Observable<StockMasterImportResult> {
    const data = new FormData(); data.append('file', file);
    return this.http.post<StockMasterImportResult>(`${this.url}/import-csv`, data);
  }
}
