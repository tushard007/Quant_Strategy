import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
export interface ETFMaster { id:number; symbol:string; underlying:string; securityName:string; dateOfListing:string; marketLot:number; isinNumber:string; faceValue:number; isCommodity:boolean; }
export type ETFMasterPayload = Omit<ETFMaster,'id'>;
export interface ETFImportResult { totalRecords:number; created:number; updated:number; removed:number; message:string; }
@Injectable({providedIn:'root'}) export class ETFMasterService {
  private readonly http=inject(HttpClient); private readonly url='/api/etf-master';
  list(search=''):Observable<ETFMaster[]>{const params=search.trim()?new HttpParams().set('search',search.trim()):undefined;return this.http.get<ETFMaster[]>(this.url,{params});}
  create(value:ETFMasterPayload){return this.http.post<ETFMaster>(this.url,value);} update(id:number,value:ETFMasterPayload){return this.http.put<ETFMaster>(`${this.url}/${id}`,value);} delete(id:number){return this.http.delete<void>(`${this.url}/${id}`);}
  saveCommoditySelection(scopeIds:number[],selectedIds:number[]){return this.http.put<ETFMaster[]>(`${this.url}/commodity-selection`,{scopeIds,selectedIds});}
  importCsv(file:File){const data=new FormData();data.append('file',file);return this.http.post<ETFImportResult>(`${this.url}/import-csv`,data);}
}
