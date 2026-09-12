import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

export type NiftyIndexName='NIFTY50'|'NIFTY500'|'NIFTY750'|'NIFTY_MIDCAP150'|'NIFTY_NEXT50'|'NIFTY_SMALLCAP250'|'NIFTY200';
export interface NiftyIndexStockDetail{symbol:string;nameOfCompany:string;series:string;isinNumber:string;industry:string;}
export interface NiftyIndexStockImportResponse{indexName:string;totalRows:number;message:string;}

@Injectable({providedIn:'root'})
export class NiftyIndexStockService{
 private readonly baseUrl='/api/nifty-index-stock';
 constructor(private readonly http:HttpClient){}
 listForIndex(indexName:NiftyIndexName):Observable<NiftyIndexStockDetail[]>{
  return this.http.get<NiftyIndexStockDetail[]>(`${this.baseUrl}/${indexName}`);
 }
 replaceWithSymbols(indexName:NiftyIndexName,symbols:string[]):Observable<NiftyIndexStockImportResponse>{
  return this.http.post<NiftyIndexStockImportResponse>(`${this.baseUrl}/${indexName}/bulk`,symbols);
 }
 importCsv(indexName:NiftyIndexName,file:File):Observable<NiftyIndexStockImportResponse>{
  const formData=new FormData();
  formData.append('file',file);
  return this.http.post<NiftyIndexStockImportResponse>(`${this.baseUrl}/${indexName}/import`,formData);
 }
}
