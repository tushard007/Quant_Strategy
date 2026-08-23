import {HttpClient,HttpParams} from '@angular/common/http';
import {inject,Injectable} from '@angular/core';
import {Observable} from 'rxjs';
export interface IndexMaster{id:number;symbol:string;indexName:string;instrumentKey:string} export type IndexMasterPayload=Omit<IndexMaster,'id'>;
@Injectable({providedIn:'root'}) export class IndexMasterService{private readonly http=inject(HttpClient);private readonly url='/api/index-master';list(search=''):Observable<IndexMaster[]>{const params=search.trim()?new HttpParams().set('search',search.trim()):undefined;return this.http.get<IndexMaster[]>(this.url,{params});}create(value:IndexMasterPayload){return this.http.post<IndexMaster>(this.url,value);}update(id:number,value:IndexMasterPayload){return this.http.put<IndexMaster>(`${this.url}/${id}`,value);}delete(id:number){return this.http.delete<void>(`${this.url}/${id}`);}}
